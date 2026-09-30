package io.turnbase.harness;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.turnbase.gdk.context.PlayerLeftReason;
import io.turnbase.gdk.effect.BroadcastEffect;
import io.turnbase.gdk.effect.Effect;
import io.turnbase.gdk.effect.GameOverEffect;
import io.turnbase.gdk.effect.LogEffect;
import io.turnbase.gdk.effect.SendToPlayerEffect;
import io.turnbase.gdk.effect.SendToTeamEffect;
import io.turnbase.gdk.model.Player;
import io.turnbase.gdk.model.Team;
import io.turnbase.serialization.GdkSerialization;
import io.turnbase.serialization.SerializationLimits;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * The in-process stand-in for the platform's Orchestration Service and WebSocket Gateway ({@code
 * test-harness.md} §2, FR-HARNESS-001/003). It speaks the game session protocol of {@code
 * websocket.md} — STOMP destinations, the {@code GAME_*} envelope with {@code sequence}/{@code
 * index}, the sync flow, per-effect fan-out — over whatever transport hands it frames, so the shell
 * in a browser tab is exercising the real wire and not a harness dialect.
 *
 * <p>Every method that touches the game is {@code synchronized}: one session, one input at a time,
 * the same ordering the platform's per-room serialization gives. What the harness does <em>not</em>
 * reproduce (rate limits, the single-flight lock across pods) is listed in {@code test-harness.md}
 * §7.
 */
public final class MockOrchestration {

  /** The harness-only destination on which the shell is told to reload its iframe. */
  static final String CONTROL_QUEUE = "/user/queue/harness";

  private static final String ERRORS_QUEUE = "/user/queue/errors";
  private static final String GAME_QUEUE = "/user/queue/game";
  private static final ObjectMapper JSON = new ObjectMapper();

  /** The transport half of one client. */
  public interface Link {
    /**
     * @param text one STOMP frame
     */
    void send(String text);

    /** Ends the connection. */
    void close();
  }

  /** A participant of the session: a player, or the one spectator. */
  private record Participant(String name, @Nullable Player player) {
    boolean spectator() {
      return player == null;
    }
  }

  private record LogLine(long sequence, int index, String entry) {}

  private final LocalGame game;
  private final String sessionId;
  private final boolean midGameJoin;
  private final Consumer<String> warnings;
  private final Map<String, Participant> participants = new LinkedHashMap<>();
  private final Set<UUID> teamIds = new LinkedHashSet<>();
  private final Set<UUID> joinedLate = new LinkedHashSet<>();
  private final Set<UUID> left = new LinkedHashSet<>();
  private final List<Team> teams;
  private final List<LogLine> log = new ArrayList<>();
  private final List<StompSession> sessions = new ArrayList<>();
  private final GdkSerialization serialization = new GdkSerialization();
  private final AtomicLong subscriptionIds = new AtomicLong();
  private long sequence;
  private boolean over;
  private boolean failed;

  /**
   * @param game the in-process game, already initialised or about to be via {@link #start()}
   * @param sessionId the session id the shell's bootstrap carries
   * @param spectators whether the manifest declares {@code capabilities.spectators}: one spectator
   *     named {@value #SPECTATOR} is then included
   * @param midGameJoin whether the manifest declares {@code capabilities.midGameJoin}
   * @param warnings receives harness warnings
   */
  public MockOrchestration(
      LocalGame game,
      String sessionId,
      boolean spectators,
      boolean midGameJoin,
      Consumer<String> warnings) {
    this.game = game;
    this.sessionId = sessionId;
    this.midGameJoin = midGameJoin;
    this.warnings = warnings;
    this.teams = game.teams();
    for (Team t : teams) {
      teamIds.add(t.id());
      for (Player p : t.players()) {
        participants.put(key(p.displayName()), new Participant(p.displayName(), p));
      }
    }
    if (spectators) {
      participants.put(key(SPECTATOR), new Participant(SPECTATOR, null));
    }
  }

  /** The spectator's name, when the manifest allows one. */
  public static final String SPECTATOR = "spectator";

  private static String key(String name) {
    return name.toLowerCase(Locale.ROOT);
  }

  /**
   * @return the session id
   */
  public String sessionId() {
    return sessionId;
  }

  /**
   * Fans out {@code onInit}'s effects at sequence 0. Nobody is subscribed yet, which is exactly the
   * platform's start-of-game race ({@code websocket.md}, "Catch-up") — the log lines are retained
   * and reach a client through its sync.
   *
   * @param effects what {@code onInit} returned
   */
  public synchronized void start(List<Effect> effects) {
    fanOut(0, effects);
  }

  /**
   * @return every participant's name, players first
   */
  public synchronized List<String> participantNames() {
    return participants.values().stream().map(Participant::name).toList();
  }

  /**
   * @param name a participant
   * @return the id of the team the named player is on, or {@code null} for the spectator or an
   *     unknown name
   */
  public synchronized @Nullable String teamIdOf(String name) {
    Participant p = participants.get(key(name));
    return p == null || p.player() == null ? null : p.player().teamId().toString();
  }

  /**
   * @param name a name as typed
   * @return the participant's canonical display name, or {@code null} when there is none
   */
  public synchronized @Nullable String canonicalName(String name) {
    Participant p = participants.get(key(name));
    return p == null ? null : p.name();
  }

  /**
   * @return a JSON description of the roster, for the shell's panel
   */
  public synchronized ObjectNode describe() {
    ObjectNode out = JSON.createObjectNode();
    out.put("sessionId", sessionId);
    ArrayNode ts = out.putArray("teams");
    for (Team t : teams) {
      ObjectNode tn = ts.addObject();
      tn.put("id", t.id().toString());
      tn.put("name", t.name());
      ArrayNode ps = tn.putArray("players");
      t.players().forEach(p -> ps.add(p.displayName()));
    }
    out.put("spectator", participants.containsKey(key(SPECTATOR)));
    out.put("midGameJoin", midGameJoin);
    return out;
  }

  /**
   * Opens a STOMP session on a connection.
   *
   * @param playerName the {@code ?player=} the tab connected as
   * @param link the transport
   * @return the session; feed it frames with {@link StompSession#receive(String)}
   */
  public synchronized StompSession connect(@Nullable String playerName, Link link) {
    StompSession s =
        new StompSession(playerName == null ? null : participants.get(key(playerName)), link);
    sessions.add(s);
    return s;
  }

  // ---- inputs: what a browser tab, the debug panel, the CLI or a scenario can each cause ----

  /**
   * Delivers an {@code action} as a player, as if their tab had sent it.
   *
   * @param playerName the acting player
   * @param actionJson the opaque action payload
   * @throws IllegalArgumentException when the name is not a player, or the session is over
   */
  public synchronized void action(String playerName, String actionJson) {
    Player player = playerOf(playerName);
    input(() -> game.action(player, actionJson), null);
  }

  /**
   * Delivers {@code player_joined}.
   *
   * @param playerName a player of the roster
   * @throws IllegalArgumentException when the manifest does not declare {@code midGameJoin}
   */
  public synchronized void playerJoined(String playerName) {
    Player player = playerOf(playerName);
    if (!midGameJoin) {
      throw new IllegalArgumentException(
          "the manifest does not declare capabilities.midGameJoin; the platform would never send player_joined");
    }
    joinedLate.add(player.id());
    input(
        () -> game.playerJoined(player), envelope("GAME_PLAYER_JOINED", playerJson(player), null));
  }

  /**
   * Delivers {@code player_left}.
   *
   * @param playerName a player of the roster
   * @param reason why
   */
  public synchronized void playerLeft(String playerName, PlayerLeftReason reason) {
    Player player = playerOf(playerName);
    left.add(player.id());
    ObjectNode payload = playerJson(player);
    input(
        () -> game.playerLeft(player, reason),
        envelope("GAME_PLAYER_LEFT", payload, reason.name()));
  }

  /**
   * Forces a resync: runs {@code onResync} for the viewer, warning on an empty view, and tells that
   * viewer's tabs to reload their iframe, which then takes the normal sync path ({@code
   * frontend-gdk-api.md} §4).
   *
   * @param playerName a player or the spectator
   */
  public synchronized void resync(String playerName) {
    Participant p = participant(playerName);
    game.resync(p.player());
    for (StompSession s : sessions) {
      if (s.who == p) {
        s.deliver(CONTROL_QUEUE, "{\"type\":\"HARNESS_RELOAD\"}");
      }
    }
  }

  private Participant participant(String name) {
    Participant p = participants.get(key(name));
    if (p == null) {
      throw new IllegalArgumentException(
          "no participant named '" + name + "'; known: " + participantNames());
    }
    return p;
  }

  private Player playerOf(String name) {
    Player p = participant(name).player();
    if (p == null) {
      throw new IllegalArgumentException(name + " is the spectator; a spectator has no inputs");
    }
    return p;
  }

  private interface Handler {
    List<Effect> run();
  }

  /** One input: allocate a sequence, run the handler, then fan out lifecycle event and effects. */
  private void input(Handler handler, @Nullable ObjectNode lifecycle) {
    if (over || failed) {
      throw new IllegalArgumentException(
          "the session has ended; the platform accepts no more input");
    }
    long seq = ++sequence;
    List<Effect> effects;
    try {
      effects = handler.run();
    } catch (SerializationMismatchException e) {
      failed = true;
      warnings.accept("FAILED: " + e.getMessage());
      broadcastRaw(
          sessionMessage(
              "SESSION_FAILED",
              null,
              null,
              JSON.createObjectNode()
                  .put("reason", "GAME_ERROR")
                  .put("message", "The game encountered an error and could not continue.")));
      throw e;
    }
    if (lifecycle != null) {
      lifecycle.put("sequence", seq);
      lifecycle.put("index", -1);
      broadcastRaw(lifecycle);
    }
    fanOut(seq, effects);
  }

  private ObjectNode envelope(String type, ObjectNode player, @Nullable String reason) {
    ObjectNode payload = JSON.createObjectNode();
    payload.set("player", player);
    if (reason != null) {
      payload.put("reason", reason);
    }
    ObjectNode env = JSON.createObjectNode();
    env.put("type", type);
    env.put("sessionId", sessionId);
    env.put("sequence", 0);
    env.put("index", -1);
    env.set("payload", payload);
    return env;
  }

  private void fanOut(long seq, List<Effect> effects) {
    for (int i = 0; i < effects.size(); i++) {
      Effect e = effects.get(i);
      switch (e) {
        case BroadcastEffect b ->
            broadcastRaw(sessionMessage("GAME_EVENT", seq, i, payload(b.payload())));
        case SendToPlayerEffect p -> {
          Participant target = playerById(p.playerId());
          if (target == null) {
            warnings.accept(
                "send_to_player dropped: "
                    + p.playerId()
                    + " is not a participant of this session (the platform drops it and counts"
                    + " turnbase.effect.address_rejected.total{kind=\"player\"})");
          } else {
            String text = sessionMessage("GAME_EVENT", seq, i, payload(p.payload())).toString();
            sessions.stream()
                .filter(s -> s.who == target)
                .forEach(s -> s.deliver(GAME_QUEUE, text));
          }
        }
        case SendToTeamEffect t -> {
          if (!teamIds.contains(t.teamId())) {
            warnings.accept(
                "send_to_team dropped: "
                    + t.teamId()
                    + " is not a team of this session (the platform drops it and counts"
                    + " turnbase.effect.address_rejected.total{kind=\"team\"})");
          } else {
            String text = sessionMessage("GAME_EVENT", seq, i, payload(t.payload())).toString();
            String dest = "/topic/game." + sessionId + ".team." + t.teamId();
            sessions.forEach(s -> s.deliverIfSubscribed(dest, text));
          }
        }
        case LogEffect l -> {
          log.add(new LogLine(seq, i, l.text()));
          broadcastRaw(
              sessionMessage("GAME_LOG", seq, i, JSON.createObjectNode().put("entry", l.text())));
        }
        case GameOverEffect g -> {
          over = true;
          ObjectNode results = JSON.createObjectNode();
          results.set("results", payload(g.resultsPayload()));
          broadcastRaw(sessionMessage("GAME_OVER", seq, i, results));
        }
      }
    }
  }

  private @Nullable Participant playerById(UUID id) {
    for (Participant p : participants.values()) {
      if (p.player() != null && p.player().id().equals(id) && !left.contains(id)) {
        return p;
      }
    }
    return null;
  }

  private JsonNode payload(Object value) {
    try {
      return JSON.readTree(
          serialization.writePayload(value, SerializationLimits.DEFAULT_MAX_STATE_BYTES));
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  private ObjectNode sessionMessage(
      String type, @Nullable Long seq, @Nullable Integer index, JsonNode payload) {
    ObjectNode env = JSON.createObjectNode();
    env.put("type", type);
    env.put("sessionId", sessionId);
    if (seq != null) {
      env.put("sequence", seq);
    }
    if (index != null) {
      env.put("index", index);
    }
    env.set("payload", payload);
    return env;
  }

  private ObjectNode sessionMessage(String type, long seq, int index, JsonNode payload) {
    return sessionMessage(type, Long.valueOf(seq), Integer.valueOf(index), payload);
  }

  private ObjectNode playerJson(Player p) {
    return JSON.createObjectNode()
        .put("id", p.id().toString())
        .put("displayName", p.displayName())
        .put("teamId", p.teamId().toString());
  }

  private void broadcastRaw(ObjectNode message) {
    String text = message.toString();
    String dest = "/topic/game." + sessionId;
    sessions.forEach(s -> s.deliverIfSubscribed(dest, text));
  }

  // ---- one STOMP connection ----

  /** One client's STOMP session. */
  public final class StompSession {
    private final @Nullable Participant who;
    private final Link link;
    private final Map<String, String> subscriptionByDestination = new LinkedHashMap<>();
    private boolean connected;

    StompSession(@Nullable Participant who, Link link) {
      this.who = who;
      this.link = link;
    }

    /**
     * Handles one inbound STOMP frame.
     *
     * @param text the frame
     */
    public void receive(String text) {
      Stomp.Frame frame = Stomp.parse(text);
      if (frame == null) {
        return;
      }
      synchronized (MockOrchestration.this) {
        try {
          switch (frame.command()) {
            case "CONNECT", "STOMP" -> connect();
            case "SUBSCRIBE" -> subscribe(frame);
            case "UNSUBSCRIBE" -> subscriptionByDestination.values().remove(frame.header("id"));
            case "SEND" -> send(frame);
            case "DISCONNECT" -> link.close();
            default -> fail("unsupported frame " + frame.command());
          }
        } catch (ProtocolException e) {
          fail(String.valueOf(e.getMessage()));
        }
      }
    }

    /** Called when the transport closes. */
    public void closed() {
      synchronized (MockOrchestration.this) {
        sessions.remove(this);
      }
    }

    private void connect() {
      if (who == null) {
        throw new ProtocolException(
            "unknown player; connect with ?player=<name>, one of " + participantNames());
      }
      connected = true;
      link.send(
          Stomp.format(
              "CONNECTED",
              Map.of("version", "1.2", "user-name", who.name(), "heart-beat", "0,0"),
              ""));
    }

    private void subscribe(Stomp.Frame frame) {
      requireConnected();
      String dest = frame.header("destination");
      String id = frame.header("id");
      if (dest == null || id == null) {
        throw new ProtocolException("SUBSCRIBE needs destination and id");
      }
      String game = "/topic/game." + sessionId;
      if (dest.startsWith(game + ".team.")) {
        Player p = who == null ? null : who.player();
        if (p == null) {
          // websocket.md FR-FE-006: spectators are refused team topics outright.
          throw new ProtocolException("spectators may not subscribe to a team topic");
        }
        if (!dest.equals(game + ".team." + p.teamId())) {
          throw new ProtocolException("not a member of that team");
        }
      } else if (!dest.equals(game)
          && !dest.equals(GAME_QUEUE)
          && !dest.equals(ERRORS_QUEUE)
          && !dest.equals(CONTROL_QUEUE)) {
        throw new ProtocolException("unknown destination " + dest);
      }
      subscriptionByDestination.put(dest, id);
    }

    private void send(Stomp.Frame frame) {
      requireConnected();
      String dest = frame.header("destination");
      JsonNode body;
      try {
        body = JSON.readTree(frame.body());
      } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
        throw new ProtocolException("body is not JSON");
      }
      String correlationId = body.path("correlationId").asText("");
      String base = "/app/game." + sessionId;
      try {
        if (base.concat(".sync").equals(dest)) {
          sync(correlationId);
        } else if (base.concat(".action").equals(dest)) {
          submit(body);
        } else {
          throw new ProtocolException("unknown destination " + dest);
        }
      } catch (IllegalArgumentException | SerializationMismatchException e) {
        error(correlationId, String.valueOf(e.getMessage()));
      }
    }

    private void sync(String correlationId) {
      if (over || failed) {
        throw new IllegalArgumentException("the session is no longer active");
      }
      Participant me = requireWho();
      ObjectNode init = JSON.createObjectNode();
      init.put("sessionId", sessionId);
      init.put("role", me.spectator() ? "spectator" : "player");
      Player self = me.player();
      if (self == null) {
        init.putNull("self");
      } else {
        init.set("self", playerJson(self));
      }
      ArrayNode roster = init.putArray("roster");
      ArrayNode ts = init.putArray("teams");
      for (Team t : teams) {
        ObjectNode tn = ts.addObject();
        tn.put("id", t.id().toString());
        tn.put("name", t.name());
        ArrayNode tp = tn.putArray("players");
        for (Player p : t.players()) {
          roster.add(playerJson(p));
          tp.add(playerJson(p));
        }
      }
      ObjectNode payload = JSON.createObjectNode();
      payload.set("init", init);
      payload.set("view", payload(game.resync(self).view()));
      ArrayNode entries = payload.putArray("log");
      for (LogLine l : log) {
        entries
            .addObject()
            .put("sequence", l.sequence())
            .put("index", l.index())
            .put("entry", l.entry());
      }
      ObjectNode msg = JSON.createObjectNode();
      msg.put("type", "GAME_SYNC");
      msg.put("sessionId", sessionId);
      msg.put("asOfSequence", sequence);
      msg.put("correlationId", correlationId);
      msg.set("payload", payload);
      deliver(GAME_QUEUE, msg.toString());
    }

    private void submit(JsonNode body) {
      Participant me = requireWho();
      if (me.spectator()) {
        throw new IllegalArgumentException("spectators cannot submit actions");
      }
      // Player identity is the connection's principal, never a field of the payload
      // (FR-FA-002, NFR-SEC-001).
      action(me.name(), body.path("payload").toString());
    }

    private Participant requireWho() {
      if (who == null) {
        throw new ProtocolException("not connected");
      }
      return who;
    }

    private void requireConnected() {
      if (!connected) {
        throw new ProtocolException("send CONNECT first");
      }
    }

    private void error(String correlationId, String message) {
      ObjectNode payload = JSON.createObjectNode().put("message", message);
      ObjectNode env = JSON.createObjectNode();
      env.put("type", "ERROR");
      env.put("correlationId", correlationId);
      env.set("payload", payload);
      deliver(ERRORS_QUEUE, env.toString());
    }

    private void fail(String message) {
      link.send(Stomp.format("ERROR", Map.of("message", message), message));
      link.close();
    }

    void deliverIfSubscribed(String destination, String text) {
      if (subscriptionByDestination.containsKey(destination)) {
        deliver(destination, text);
      }
    }

    void deliver(String destination, String text) {
      String sub = subscriptionByDestination.get(destination);
      if (sub == null) {
        return;
      }
      link.send(
          Stomp.format(
              "MESSAGE",
              Map.of(
                  "destination",
                  destination,
                  "subscription",
                  sub,
                  "message-id",
                  Long.toString(subscriptionIds.incrementAndGet()),
                  "content-type",
                  "application/json"),
              text));
    }
  }

  /** A client broke the protocol; the connection is closed with an ERROR frame. */
  private static final class ProtocolException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    ProtocolException(String message) {
      super(message);
    }
  }
}
