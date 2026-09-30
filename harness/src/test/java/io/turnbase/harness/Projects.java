package io.turnbase.harness;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

/** Builds throwaway game projects: a real game compiled against the real GDK. */
final class Projects {

  private Projects() {}

  static final String MANIFEST =
      """
      {
        "manifestVersion":"1","title":"Counter","shortDescription":"Counts",
        "version":"1.0.0","gdkApiVersion":"0.3.0","runtime":"jvm",
        "backend":{"path":"backend/game.jar","entryPoint":"com.example.CounterGame"},
        "frontend":{"entryPoint":"frontend/main.js"},
        "tags":["party"],
        "capabilities":{"ai":false,"spectators":false,"midGameJoin":false},
        "teamTemplates":[{"size":{"min":1,"max":2},"count":{"min":2,"max":2}}]
      }
      """;

  /** A game whose state is {@code stateSource} (a top-level class named CounterState). */
  static Path project(Path dir, String stateSource) throws IOException {
    String game =
        """
        package com.example;
        import io.turnbase.gdk.*;
        import io.turnbase.gdk.context.*;
        import io.turnbase.gdk.result.*;
        public final class CounterGame implements TurnBaseGame<CounterState> {
          public CounterGame() {}
          public InitResult<CounterState> onInit(InitContext ctx) {
            return InitResult.of(CounterState.initial());
          }
          public ActionResult<CounterState> onAction(CounterState s, ActionContext ctx) {
            return ActionResult.of(s.next());
          }
          public PlayerLeftResult<CounterState> onPlayerLeft(CounterState s, PlayerLeftContext c) {
            return PlayerLeftResult.of(s);
          }
          public ViewResult onResync(CounterState s, ResyncContext c) { return ViewResult.empty(); }
        }
        """;
    Path work = Files.createDirectories(dir.resolve("work"));
    Files.writeString(work.resolve("CounterGame.java"), game);
    Files.writeString(work.resolve("CounterState.java"), stateSource);
    Path out = Files.createDirectories(dir.resolve("classes"));
    JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
    ByteArrayOutputStream err = new ByteArrayOutputStream();
    int rc =
        javac.run(
            null,
            null,
            err,
            "-parameters",
            "-cp",
            System.getProperty("java.class.path"),
            "-d",
            out.toString(),
            work.resolve("CounterGame.java").toString(),
            work.resolve("CounterState.java").toString());
    if (rc != 0) {
      throw new IllegalStateException(err.toString(java.nio.charset.StandardCharsets.UTF_8));
    }
    Path project = Files.createDirectories(dir.resolve("project"));
    Files.writeString(project.resolve("manifest.json"), MANIFEST);
    Files.createDirectories(project.resolve("backend"));
    Files.write(project.resolve("backend/game.jar"), jar(out));
    Files.createDirectories(project.resolve("frontend"));
    Files.writeString(project.resolve("frontend/main.js"), "console.log('hi');");
    return project;
  }

  private static byte[] jar(Path classes) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(bytes);
        var files = Files.walk(classes)) {
      for (Path f : files.filter(Files::isRegularFile).toList()) {
        zip.putNextEntry(new ZipEntry(classes.relativize(f).toString().replace('\\', '/')));
        zip.write(Files.readAllBytes(f));
        zip.closeEntry();
      }
    } catch (UncheckedIOException e) {
      throw e.getCause();
    }
    return bytes.toByteArray();
  }

  /** A state that survives the round trip. */
  static final String GOOD_STATE =
      """
      package com.example;
      public record CounterState(int count) implements io.turnbase.gdk.GameState {
        static CounterState initial() { return new CounterState(0); }
        CounterState next() { return new CounterState(count + 1); }
      }
      """;

  /** A state that does not: a runtime subtype in an Object field comes back as a Map. */
  static final String BAD_STATE =
      """
      package com.example;
      public record CounterState(Object payload, int count) implements io.turnbase.gdk.GameState {
        public record Tag(String v) {}
        static CounterState initial() { return new CounterState(new Tag("x"), 0); }
        CounterState next() { return new CounterState(payload, count + 1); }
      }
      """;

  static List<String> none() {
    return List.of();
  }
}
