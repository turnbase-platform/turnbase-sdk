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
 * {@code mvn io.turnbase:turnbase-harness:run} — validates the game project in the current
 * directory, starts it in-process and serves the two-origin harness until interrupted ({@code
 * test-harness.md} §6). Needs no project model, so it works from any directory containing a {@code
 * manifest.json}, and needs no Docker, Redis or database.
 */
@Mojo(name = "run", requiresProject = false, requiresDependencyResolution = ResolutionScope.NONE)
public class RunMojo extends AbstractMojo {

  /** The game project directory: an unpacked bundle ({@code test-harness.md} §8). */
  @Parameter(property = "turnbase.harness.dir", defaultValue = "${user.dir}")
  private String dir = ".";

  /** Overrides {@code harness.json}'s port; -1 keeps it. */
  @Parameter(property = "turnbase.harness.port", defaultValue = "-1")
  private int port;

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    HarnessServer server;
    try {
      server = HarnessLauncher.launch(Path.of(dir).toAbsolutePath().normalize(), port, System.out);
    } catch (HarnessLauncher.LaunchRefusedException e) {
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
