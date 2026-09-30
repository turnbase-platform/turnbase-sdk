package io.turnbase.harness;

import com.fasterxml.jackson.databind.JsonNode;
import io.turnbase.gdk.context.PlayerLeftReason;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * One mock input for a chosen player ({@code test-harness.md} §4–§5, FR-HARNESS-005): the same
 * shape whether it arrives from the debug panel, a {@code harness.json} scenario entry or a CLI
 * flag, so there is one thing to learn and one thing to test.
 *
 * <p>JSON form — {@code player} plus exactly one of: {@code "action": <payload>}, {@code "join":
 * true}, {@code "leave": "left"|"kicked"|"grace_expired"}, {@code "resync": true}.
 *
 * @param kind what to deliver
 * @param player the chosen player's display name
 * @param action the action payload as JSON, for {@link Kind#ACTION}
 * @param reason why the player left, for {@link Kind#LEAVE}
 */
public record DebugCommand(
    Kind kind, String player, @Nullable String action, @Nullable PlayerLeftReason reason) {

  /** The four inputs a tab, panel, CLI or scenario can cause. */
  public enum Kind {
    /** {@code action}. */
    ACTION,
    /** {@code player_joined}. */
    JOIN,
    /** {@code player_left}. */
    LEAVE,
    /** {@code resync}. */
    RESYNC
  }

  /**
   * @param json the JSON form
   * @return the command
   * @throws IllegalArgumentException when it names no player or not exactly one input
   */
  public static DebugCommand fromJson(JsonNode json) {
    String player = json.path("player").asText("");
    if (player.isEmpty()) {
      throw new IllegalArgumentException("a command needs a \"player\"");
    }
    int given = 0;
    for (String k : new String[] {"action", "join", "leave", "resync"}) {
      if (json.has(k)) {
        given++;
      }
    }
    if (given != 1) {
      throw new IllegalArgumentException(
          "a command needs exactly one of action, join, leave, resync");
    }
    if (json.has("action")) {
      return new DebugCommand(Kind.ACTION, player, json.get("action").toString(), null);
    } else if (json.has("join")) {
      return new DebugCommand(Kind.JOIN, player, null, null);
    } else if (json.has("leave")) {
      return new DebugCommand(Kind.LEAVE, player, null, reason(json.get("leave").asText("left")));
    }
    return new DebugCommand(Kind.RESYNC, player, null, null);
  }

  /**
   * Parses {@code --player NAME} followed by one of {@code --action JSON}, {@code --join}, {@code
   * --leave [REASON]} or {@code --resync}; repeat {@code --player} for more commands.
   *
   * @param args the flags
   * @return the commands, in order
   * @throws IllegalArgumentException on an unknown or incomplete flag
   */
  public static List<DebugCommand> fromArgs(List<String> args) {
    List<DebugCommand> out = new ArrayList<>();
    String player = null;
    for (int i = 0; i < args.size(); i++) {
      String a = args.get(i);
      switch (a) {
        case "--player" -> player = value(args, ++i, a);
        case "--action" ->
            out.add(new DebugCommand(Kind.ACTION, named(player), value(args, ++i, a), null));
        case "--join" -> out.add(new DebugCommand(Kind.JOIN, named(player), null, null));
        case "--resync" -> out.add(new DebugCommand(Kind.RESYNC, named(player), null, null));
        case "--leave" -> {
          String reason =
              i + 1 < args.size() && !args.get(i + 1).startsWith("--") ? args.get(++i) : "left";
          out.add(new DebugCommand(Kind.LEAVE, named(player), null, reason(reason)));
        }
        default -> throw new IllegalArgumentException("unknown flag " + a);
      }
    }
    return out;
  }

  private static String named(@Nullable String player) {
    if (player == null) {
      throw new IllegalArgumentException("--player must come before the input it applies to");
    }
    return player;
  }

  private static String value(List<String> args, int i, String flag) {
    if (i >= args.size()) {
      throw new IllegalArgumentException(flag + " needs a value");
    }
    return args.get(i);
  }

  static PlayerLeftReason reason(String text) {
    return switch (text.toLowerCase(Locale.ROOT)) {
      case "left" -> PlayerLeftReason.LEFT;
      case "kicked" -> PlayerLeftReason.KICKED;
      case "grace_expired", "grace_period_expired" -> PlayerLeftReason.GRACE_PERIOD_EXPIRED;
      default ->
          throw new IllegalArgumentException(
              "reason must be left, kicked or grace_expired, not " + text);
    };
  }

  /**
   * Delivers this command.
   *
   * @param orchestration the mock Orchestration
   */
  public void applyTo(MockOrchestration orchestration) {
    switch (kind) {
      case ACTION -> orchestration.action(player, action == null ? "{}" : action);
      case JOIN -> orchestration.playerJoined(player);
      case LEAVE ->
          orchestration.playerLeft(player, reason == null ? PlayerLeftReason.LEFT : reason);
      case RESYNC -> orchestration.resync(player);
    }
  }
}
