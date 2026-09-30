package io.turnbase.harness;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * {@code harness.json}, beside the manifest ({@code test-harness.md} §8). Every field is optional.
 *
 * @param port the harness shell port; the game origin uses the same port
 * @param players the configured roster, or empty to derive it from the manifest's {@code
 *     teamTemplates}
 * @param parameters overrides for the manifest's host-configurable parameters
 * @param scenario ordered actions to replay at start
 * @param strictSerialization fail the run on a round-trip mismatch rather than warn
 * @param failOnWarnings treat validator warnings as fatal, for CI
 */
public record HarnessConfig(
    int port,
    List<PlayerSpec> players,
    Map<String, JsonNode> parameters,
    List<JsonNode> scenario,
    boolean strictSerialization,
    boolean failOnWarnings) {

  /** The file name, beside {@code manifest.json}. */
  public static final String FILE_NAME = "harness.json";

  /** {@code port}'s default. */
  public static final int DEFAULT_PORT = 7777;

  /**
   * One configured player.
   *
   * @param name the display name
   * @param team the 1-based team index the player joins, or {@code null} to fill teams in order
   */
  public record PlayerSpec(String name, @Nullable Integer team) {}

  /**
   * @return every default of {@code test-harness.md} §8
   */
  public static HarnessConfig defaults() {
    return new HarnessConfig(DEFAULT_PORT, List.of(), Map.of(), List.of(), true, false);
  }

  /**
   * Reads {@code harness.json} from a project directory.
   *
   * @param projectDir the game project directory
   * @return the configuration; {@link #defaults()} when the file is absent
   * @throws IOException when the file exists and cannot be read or parsed
   */
  public static HarnessConfig load(Path projectDir) throws IOException {
    Path file = projectDir.resolve(FILE_NAME);
    if (!Files.isRegularFile(file)) {
      return defaults();
    }
    JsonNode root = new ObjectMapper().readTree(Files.readAllBytes(file));
    List<PlayerSpec> players = new ArrayList<>();
    for (JsonNode p : root.path("players")) {
      players.add(
          new PlayerSpec(
              p.path("name").asText(), p.hasNonNull("team") ? p.get("team").asInt() : null));
    }
    Map<String, JsonNode> parameters = new LinkedHashMap<>();
    root.path("parameters").properties().forEach(e -> parameters.put(e.getKey(), e.getValue()));
    List<JsonNode> scenario = new ArrayList<>();
    root.path("scenario").forEach(scenario::add);
    return new HarnessConfig(
        root.path("port").asInt(DEFAULT_PORT),
        List.copyOf(players),
        Map.copyOf(parameters),
        List.copyOf(scenario),
        root.path("strictSerialization").asBoolean(true),
        root.path("failOnWarnings").asBoolean(false));
  }
}
