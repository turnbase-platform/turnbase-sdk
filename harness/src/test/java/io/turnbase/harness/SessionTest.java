package io.turnbase.harness;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Clause 4/6: real browser-shaped clients, on the real STOMP-over-WebSocket wire. */
class SessionTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  @TempDir Path tmp;

  private final ByteArrayOutputStream captured = new ByteArrayOutputStream();
  private final PrintStream out = new PrintStream(captured, true, StandardCharsets.UTF_8);
  private final HttpClient http = HttpClient.newHttpClient();
  private HarnessServer server;
  private String sessionId;

  @BeforeEach
  void launch() throws Exception {
    server = HarnessLauncher.launch(Projects.effectsProject(tmp), 0, out);
    JsonNode roster = JSON.readTree(get("/__harness/roster").body());
    sessionId = roster.path("sessionId").asText();
  }

  @AfterEach
  void stop() {
    server.close();
  }

  private HttpResponse<String> get(String path) throws Exception {
    return http.send(
        HttpRequest.newBuilder(URI.create(server.shellOrigin() + path)).build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> debug(String json, boolean withHeader) throws Exception {
    HttpRequest.Builder b =
        HttpRequest.newBuilder(URI.create(server.shellOrigin() + "/__harness/debug"))
            .POST(HttpRequest.BodyPublishers.ofString(json));
    if (withHeader) {
      b.header("X-Harness", "1");
    }
    return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
  }

  /** A tab: connects as one player and records every STOMP frame. */
  private final class Tab {
    final BlockingQueue<Stomp.Frame> frames = new LinkedBlockingQueue<>();
    final WebSocket socket;

    Tab(String player, String origin) throws Exception {
      StringBuilder buf = new StringBuilder();
      socket =
          http.newWebSocketBuilder()
              .header("Origin", origin)
              .buildAsync(
                  URI.create(
                      "ws://localhost:"
                          + server.webSocketPort()
                          + "/ws?player="
                          + java.net.URLEncoder.encode(player, StandardCharsets.UTF_8)),
                  new WebSocket.Listener() {
                    @Override
                    public CompletionStage<?> onText(
                        WebSocket ws, CharSequence data, boolean last) {
                      buf.append(data);
                      if (last) {
                        Stomp.Frame f = Stomp.parse(buf.toString());
                        buf.setLength(0);
                        if (f != null) {
                          frames.add(f);
                        }
                      }
                      ws.request(1);
                      return CompletableFuture.completedFuture(null);
                    }
                  })
              .get(5, TimeUnit.SECONDS);
    }

    void send(String command, Map<String, String> headers, String body) {
      socket.sendText(Stomp.format(command, headers, body), true).join();
    }

    Stomp.Frame next() throws Exception {
      Stomp.Frame f = frames.poll(5, TimeUnit.SECONDS);
      assertThat(f).as("a frame within 5 s").isNotNull();
      return f;
    }

    JsonNode nextMessage() throws Exception {
      Stomp.Frame f = next();
      assertThat(f.command()).isEqualTo("MESSAGE");
      return JSON.readTree(f.body());
    }

    /** Connects and subscribes the way the shell does, then syncs. */
    JsonNode join(@Nullable String teamId) throws Exception {
      send("CONNECT", Map.of("accept-version", "1.2"), "");
      assertThat(next().command()).isEqualTo("CONNECTED");
      subscribe("g", "/topic/game." + sessionId);
      subscribe("u", "/user/queue/game");
      subscribe("e", "/user/queue/errors");
      if (teamId != null) {
        subscribe("t", "/topic/game." + sessionId + ".team." + teamId);
      }
      send(
          "SEND",
          Map.of("destination", "/app/game." + sessionId + ".sync"),
          "{\"type\":\"REQUEST_GAME_SYNC\",\"correlationId\":\"c1\"}");
      return nextMessage();
    }

    void subscribe(String id, String destination) {
      send("SUBSCRIBE", Map.of("id", id, "destination", destination), "");
    }

    void act(String json) {
      send(
          "SEND",
          Map.of("destination", "/app/game." + sessionId + ".action"),
          "{\"type\":\"SUBMIT_ACTION\",\"correlationId\":\"c2\",\"payload\":" + json + "}");
    }
  }

  private @Nullable String teamIdOf(String player) throws Exception {
    // The shell page carries it in its config; read it the way the shell does.
    String page =
        get("/?player=" + java.net.URLEncoder.encode(player, StandardCharsets.UTF_8)).body();
    String cfg = page.substring(page.indexOf("id=\"cfg\">") + 9, page.indexOf("</script>"));
    JsonNode node = JSON.readTree(cfg);
    return node.has("teamId") ? node.get("teamId").asText() : null;
  }

  @Test
  void syncCarriesInitContextViewAndTheRetainedLog() throws Exception {
    Tab alice = new Tab("Player 1.1", server.shellOrigin());
    JsonNode sync = alice.join(teamIdOf("Player 1.1"));
    assertThat(sync.path("type").asText()).isEqualTo("GAME_SYNC");
    assertThat(sync.path("asOfSequence").asLong()).isZero();
    assertThat(sync.path("correlationId").asText()).isEqualTo("c1");
    JsonNode init = sync.path("payload").path("init");
    assertThat(init.path("role").asText()).isEqualTo("player");
    assertThat(init.path("self").path("displayName").asText()).isEqualTo("Player 1.1");
    assertThat(init.path("roster")).hasSize(4);
    assertThat(init.path("teams")).hasSize(2);
    assertThat(sync.path("payload").path("view").path("count").asInt()).isZero();
    // onInit ran at sequence 0 while nobody was subscribed; the log line survives in the sync.
    assertThat(sync.path("payload").path("log").get(0).path("entry").asText())
        .isEqualTo("**started**");
  }

  @Test
  void actionFansOutByAudienceWithSequenceAndIndex() throws Exception {
    String team1 = teamIdOf("Player 1.1");
    Tab alice = new Tab("Player 1.1", server.shellOrigin());
    Tab mate = new Tab("Player 1.2", server.shellOrigin());
    Tab rival = new Tab("Player 2.1", server.shellOrigin());
    Tab watcher = new Tab(MockOrchestration.SPECTATOR, server.shellOrigin());
    alice.join(team1);
    mate.join(teamIdOf("Player 1.2"));
    rival.join(teamIdOf("Player 2.1"));
    watcher.join(null);

    alice.act("{}");

    JsonNode broadcast = alice.nextMessage();
    assertThat(broadcast.path("type").asText()).isEqualTo("GAME_EVENT");
    assertThat(broadcast.path("sequence").asLong()).isEqualTo(1);
    assertThat(broadcast.path("index").asInt()).isZero();
    assertThat(broadcast.path("sessionId").asText()).isEqualTo(sessionId);
    assertThat(broadcast.path("payload").path("count").asInt()).isEqualTo(1);
    JsonNode priv = alice.nextMessage();
    assertThat(priv.path("index").asInt()).isEqualTo(1);
    assertThat(priv.path("payload").path("private").asText()).isEqualTo("Player 1.1");
    JsonNode team = alice.nextMessage();
    assertThat(team.path("index").asInt()).isEqualTo(2);
    assertThat(team.path("payload").path("team").asBoolean()).isTrue();
    // index 3 is the stray send_to_player: dropped, so the next thing is the log at index 4.
    JsonNode log = alice.nextMessage();
    assertThat(log.path("type").asText()).isEqualTo("GAME_LOG");
    assertThat(log.path("index").asInt()).isEqualTo(4);

    // The team-mate hears the broadcast and the team message, never the private one.
    assertThat(mate.nextMessage().path("payload").has("count")).isTrue();
    assertThat(mate.nextMessage().path("payload").has("team")).isTrue();
    assertThat(mate.nextMessage().path("type").asText()).isEqualTo("GAME_LOG");

    // The other team and the spectator get broadcast-originated messages only.
    for (Tab t : List.of(rival, watcher)) {
      assertThat(t.nextMessage().path("payload").has("count")).isTrue();
      assertThat(t.nextMessage().path("type").asText()).isEqualTo("GAME_LOG");
    }
    assertThat(captured.toString(StandardCharsets.UTF_8)).contains("send_to_player dropped");
  }

  @Test
  void aSpectatorIsRefusedTeamTopicsAndActions() throws Exception {
    Tab watcher = new Tab(MockOrchestration.SPECTATOR, server.shellOrigin());
    watcher.join(null);
    watcher.act("{}");
    JsonNode error = watcher.nextMessage();
    assertThat(error.path("type").asText()).isEqualTo("ERROR");
    assertThat(error.path("payload").path("message").asText()).contains("spectators");

    Tab other = new Tab(MockOrchestration.SPECTATOR, server.shellOrigin());
    other.send("CONNECT", Map.of("accept-version", "1.2"), "");
    other.next();
    other.subscribe("t", "/topic/game." + sessionId + ".team." + java.util.UUID.randomUUID());
    assertThat(other.next().command()).isEqualTo("ERROR");
  }

  @Test
  void identityComesFromTheConnectionNotThePayload() throws Exception {
    Tab alice = new Tab("Player 1.1", server.shellOrigin());
    alice.join(null);
    alice.act("{\"player\":\"Player 2.1\"}");
    assertThat(alice.nextMessage().path("payload").path("count").asInt()).isEqualTo(1);
    assertThat(alice.nextMessage().path("payload").path("private").asText())
        .isEqualTo("Player 1.1");
  }

  @Test
  void lifecycleInputsArriveAtIndexMinusOneAheadOfTheirEffects() throws Exception {
    Tab alice = new Tab("Player 1.1", server.shellOrigin());
    alice.join(null);

    assertThat(debug("{\"player\":\"Player 2.1\",\"join\":true}", true).statusCode())
        .isEqualTo(200);
    JsonNode joined = alice.nextMessage();
    assertThat(joined.path("type").asText()).isEqualTo("GAME_PLAYER_JOINED");
    assertThat(joined.path("sequence").asLong()).isEqualTo(1);
    assertThat(joined.path("index").asInt()).isEqualTo(-1);
    assertThat(joined.path("payload").path("player").path("displayName").asText())
        .isEqualTo("Player 2.1");
    assertThat(alice.nextMessage().path("payload").path("welcome").asText())
        .isEqualTo("Player 2.1");

    assertThat(debug("{\"player\":\"Player 2.1\",\"leave\":\"kicked\"}", true).statusCode())
        .isEqualTo(200);
    JsonNode left = alice.nextMessage();
    assertThat(left.path("type").asText()).isEqualTo("GAME_PLAYER_LEFT");
    assertThat(left.path("sequence").asLong()).isEqualTo(2);
    assertThat(left.path("payload").path("reason").asText()).isEqualTo("KICKED");
    assertThat(alice.nextMessage().path("payload").path("bye").asText()).isEqualTo("KICKED");
  }

  @Test
  void aMockActionReachesTheGameWithoutATabAndALateTabSeesItInItsSync() throws Exception {
    assertThat(debug("{\"player\":\"Player 1.1\",\"action\":{}}", true).statusCode())
        .isEqualTo(200);
    Tab late = new Tab("Player 2.1", server.shellOrigin());
    JsonNode sync = late.join(null);
    assertThat(sync.path("asOfSequence").asLong()).isEqualTo(1);
    assertThat(sync.path("payload").path("view").path("count").asInt()).isEqualTo(1);
    assertThat(sync.path("payload").path("log")).hasSize(2);
  }

  @Test
  void aForcedResyncTellsThatViewersTabsToReload() throws Exception {
    Tab alice = new Tab("Player 1.1", server.shellOrigin());
    alice.join(null);
    alice.subscribe("h", MockOrchestration.CONTROL_QUEUE);
    Thread.sleep(200);
    assertThat(debug("{\"player\":\"Player 1.1\",\"resync\":true}", true).statusCode())
        .isEqualTo(200);
    assertThat(alice.nextMessage().path("type").asText()).isEqualTo("HARNESS_RELOAD");
  }

  @Test
  void badInputsAreRefusedWithAReason() throws Exception {
    HttpResponse<String> nobody = debug("{\"player\":\"Nobody\",\"action\":{}}", true);
    assertThat(nobody.statusCode()).isEqualTo(400);
    assertThat(nobody.body()).contains("no participant named 'Nobody'");
    assertThat(debug("{\"player\":\"Player 1.1\"}", true).statusCode()).isEqualTo(400);
    assertThat(debug("{\"player\":\"Player 1.1\",\"leave\":\"bored\"}", true).statusCode())
        .isEqualTo(400);
  }

  @Test
  void theDebugEndpointAndTheSocketRefuseAnythingButTheShell() throws Exception {
    assertThat(debug("{\"player\":\"Player 1.1\",\"action\":{}}", false).statusCode())
        .isEqualTo(403);
    // The game origin cannot open a player's connection.
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> new Tab("Player 1.1", server.gameOrigin()))
        .hasCauseInstanceOf(java.net.http.WebSocketHandshakeException.class);
  }
}
