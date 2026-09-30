package io.turnbase.harness;

import com.fasterxml.jackson.databind.JsonNode;
import io.turnbase.bundle.BundleValidator;
import io.turnbase.bundle.Finding;
import io.turnbase.bundle.Severity;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The launch sequence of {@code test-harness.md} §6: validate, load, {@code onInit}, serve. Shared
 * by the {@code run} Maven goal and {@link HarnessMain}.
 */
public final class HarnessLauncher {

  private HarnessLauncher() {}

  /** Thrown when the launch is refused; the message is the whole explanation. */
  public static final class LaunchRefusedException extends Exception {
    private static final long serialVersionUID = 1L;

    LaunchRefusedException(String message) {
      super(message);
    }
  }

  /**
   * As {@link #launch(Path, int, PrintStream, List)} with no mock inputs.
   *
   * @param projectDir the game project directory
   * @param portOverride the port to use instead of {@code harness.json}'s, or {@code -1}
   * @param out where findings and the ready line are printed
   * @return the running server
   * @throws LaunchRefusedException when the launch is refused
   * @throws IOException when the port cannot be bound
   */
  public static HarnessServer launch(Path projectDir, int portOverride, PrintStream out)
      throws LaunchRefusedException, IOException {
    return launch(projectDir, portOverride, out, List.of());
  }

  /**
   * Runs the pre-launch check, loads and initialises the game, and starts the two-origin server.
   *
   * @param projectDir the game project directory
   * @param portOverride the port to use instead of {@code harness.json}'s, or {@code -1}
   * @param out where findings and the ready line are printed
   * @param commands mock inputs to deliver once the server is up, after the {@code harness.json}
   *     scenario
   * @return the running server; closing it stops the harness
   * @throws LaunchRefusedException when a fatal finding (or, with {@code failOnWarnings}, a
   *     warning) stops the launch, or the game cannot be loaded
   * @throws IOException when {@code harness.json} is unreadable or the port cannot be bound
   */
  public static HarnessServer launch(
      Path projectDir, int portOverride, PrintStream out, List<DebugCommand> commands)
      throws LaunchRefusedException, IOException {
    HarnessConfig config = HarnessConfig.load(projectDir);
    PreLaunchCheck.Outcome check = PreLaunchCheck.run(projectDir, config);
    for (Finding f : check.findings()) {
      out.println(
          (f.severity() == Severity.ERROR ? "ERROR   " : "WARNING ")
              + f.code()
              + ": "
              + f.message());
    }
    if (!check.launch()) {
      throw new LaunchRefusedException("bundle validation failed; the harness did not launch");
    }
    BundleValidator.Bundle bundle = check.validation().bundle().orElseThrow();
    JsonNode manifest = bundle.manifest();
    LocalGame game;
    List<io.turnbase.gdk.effect.Effect> initEffects;
    try {
      game = GameLoader.load(projectDir, bundle, config, w -> out.println("WARNING " + w));
      initEffects = game.init();
    } catch (ReflectiveOperationException | SerializationMismatchException e) {
      throw new LaunchRefusedException("the game did not start: " + e);
    }
    JsonNode capabilities = manifest.path("capabilities");
    MockOrchestration orchestration =
        new MockOrchestration(
            game,
            UUID.randomUUID().toString(),
            capabilities.path("spectators").asBoolean(false),
            capabilities.path("midGameJoin").asBoolean(false),
            w -> out.println("WARNING " + w));
    orchestration.start(initEffects);
    if (!HarnessServer.gdkAvailable()) {
      out.println(
          "WARNING the FE GDK is not bundled with this harness build, so '@turnbase/gdk' cannot be"
              + " injected; a game frontend must bundle it itself.");
    }
    Set<Path> withheld = new HashSet<>();
    for (String name : new String[] {"manifest.json", HarnessConfig.FILE_NAME}) {
      withheld.add(projectDir.resolve(name).toAbsolutePath().normalize());
    }
    withheld.add(
        projectDir
            .resolve(manifest.path("backend").path("path").asText())
            .toAbsolutePath()
            .normalize());
    HarnessServer server =
        new HarnessServer(
            projectDir,
            manifest.path("frontend").path("entryPoint").asText(),
            withheld,
            portOverride >= 0 ? portOverride : config.port(),
            orchestration,
            GameLoader.parameterValues(manifest.path("parameters"), config.parameters()),
            m -> out.println("WARNING " + m));
    server.start();
    List<DebugCommand> all = new ArrayList<>();
    for (JsonNode step : config.scenario()) {
      all.add(DebugCommand.fromJson(step));
    }
    all.addAll(commands);
    try {
      for (DebugCommand c : all) {
        c.applyTo(orchestration);
      }
    } catch (IllegalArgumentException | SerializationMismatchException e) {
      server.close();
      throw new LaunchRefusedException("a scripted input failed: " + e.getMessage());
    }
    out.println(
        "Harness ready: " + server.shellOrigin() + "/  (game: " + server.gameOrigin() + ")");
    return server;
  }
}
