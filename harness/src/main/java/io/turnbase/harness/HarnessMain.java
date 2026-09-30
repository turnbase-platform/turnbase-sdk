package io.turnbase.harness;

import java.nio.file.Path;

/** {@code java -cp … io.turnbase.harness.HarnessMain [projectDir]} — the goal, without Maven. */
public final class HarnessMain {

  private HarnessMain() {}

  /**
   * @param args an optional project directory (default the working directory), then optional mock
   *     inputs: {@code --player NAME} with {@code --action JSON}, {@code --join}, {@code --leave
   *     [REASON]} or {@code --resync}
   * @throws Exception when the launch is refused
   */
  public static void main(String[] args) throws Exception {
    boolean hasDir = args.length > 0 && !args[0].startsWith("--");
    Path dir = Path.of(hasDir ? args[0] : ".").toAbsolutePath().normalize();
    java.util.List<String> flags = java.util.List.of(args).subList(hasDir ? 1 : 0, args.length);
    HarnessServer server =
        HarnessLauncher.launch(dir, -1, System.out, DebugCommand.fromArgs(flags));
    Runtime.getRuntime().addShutdownHook(new Thread(server::close));
    Thread.currentThread().join();
  }
}
