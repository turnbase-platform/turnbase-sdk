package io.turnbase.harness;

import java.io.IOException;
import java.nio.file.Path;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * {@code mvn io.github.turnbase-platform:turnbase-harness:run} — validates the game project in the
 * current directory, starts it in-process and serves the two-origin harness until interrupted
 * ({@code test-harness.md} §6). Needs no project model, so it works from any directory containing a
 * {@code manifest.json}, and needs no Docker, Redis or database.
 */
@Mojo(name = "run", requiresProject = false, requiresDependencyResolution = ResolutionScope.NONE)
public class RunMojo extends AbstractMojo {

  /** The game project directory: an unpacked bundle ({@code test-harness.md} §8). */
  @Parameter(property = "turnbase.harness.dir", defaultValue = "${user.dir}")
  private String dir = ".";

  /** Overrides {@code harness.json}'s port; -1 keeps it. */
  @Parameter(property = "turnbase.harness.port", defaultValue = "-1")
  private int port;

  /**
   * With {@code action}, {@code join}, {@code leave} or {@code resync}: the player they apply to.
   */
  @Parameter(property = "turnbase.harness.player")
  private String player = "";

  /** Deliver this action payload (JSON) as {@code player} once started. */
  @Parameter(property = "turnbase.harness.action")
  private String action = "";

  /** Deliver {@code player_joined} for {@code player} once started. */
  @Parameter(property = "turnbase.harness.join", defaultValue = "false")
  private boolean join;

  /**
   * Deliver {@code player_left} for {@code player} with this reason (left, kicked, grace_expired).
   */
  @Parameter(property = "turnbase.harness.leave")
  private String leave = "";

  /** Force a resync for {@code player} once started. */
  @Parameter(property = "turnbase.harness.resync", defaultValue = "false")
  private boolean resync;

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    HarnessServer server;
    try {
      java.util.List<String> flags = new java.util.ArrayList<>();
      if (!player.isEmpty()) {
        flags.addAll(java.util.List.of("--player", player));
      }
      if (!action.isEmpty()) {
        flags.addAll(java.util.List.of("--action", action));
      }
      if (join) {
        flags.add("--join");
      }
      if (!leave.isEmpty()) {
        flags.addAll(java.util.List.of("--leave", leave));
      }
      if (resync) {
        flags.add("--resync");
      }
      server =
          HarnessLauncher.launch(
              Path.of(dir).toAbsolutePath().normalize(),
              port,
              System.out,
              DebugCommand.fromArgs(flags));
    } catch (HarnessLauncher.LaunchRefusedException e) {
      throw new MojoFailureException(e.getMessage());
    } catch (IllegalArgumentException e) {
      throw new MojoFailureException(e.getMessage());
    } catch (IOException e) {
      throw new MojoExecutionException("could not start the harness", e);
    }
    try {
      Thread.currentThread().join();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } finally {
      server.close();
    }
  }
}
