package io.turnbase.harness;

import java.nio.file.Path;

/** {@code java -cp … io.turnbase.harness.HarnessMain [projectDir]} — the goal, without Maven. */
public final class HarnessMain {

  private HarnessMain() {}

  /**
   * @param args an optional project directory, default the working directory
   * @throws Exception when the launch is refused
   */
  public static void main(String[] args) throws Exception {
    Path dir = Path.of(args.length > 0 ? args[0] : ".").toAbsolutePath().normalize();
    HarnessServer server = HarnessLauncher.launch(dir, -1, System.out);
    Runtime.getRuntime().addShutdownHook(new Thread(server::close));
    Thread.currentThread().join();
  }
}
