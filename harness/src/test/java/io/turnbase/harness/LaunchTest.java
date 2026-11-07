package io.turnbase.harness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LaunchTest {

  @TempDir Path tmp;

  private final ByteArrayOutputStream captured = new ByteArrayOutputStream();
  private final PrintStream out = new PrintStream(captured, true, StandardCharsets.UTF_8);

  /** One raw request, so the Host header — the only thing telling the origins apart — is ours. */
  private static String get(HarnessServer server, String host, String path) throws Exception {
    try (Socket s = new Socket("127.0.0.1", server.port())) {
      s.getOutputStream()
          .write(
              ("GET "
                      + path
                      + " HTTP/1.1\r\nHost: "
                      + host
                      + ":"
                      + server.port()
                      + "\r\nConnection: close\r\n\r\n")
                  .getBytes(StandardCharsets.UTF_8));
      return new String(s.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  @Test
  void gameOriginServesTheBootstrapBeforeTheGameScript() throws Exception {
    Path project = Projects.project(tmp, Projects.GOOD_STATE);
    try (HarnessServer server = HarnessLauncher.launch(project, 0, out)) {
      String doc = get(server, "game.localhost", "/");
      assertThat(doc).startsWith("HTTP/1.1 200");
      int bootstrap = doc.indexOf("window.__TURNBASE__");
      int entry = doc.indexOf("<script type=\"module\" src=\"frontend/main.js\">");
      assertThat(bootstrap).isPositive();
      assertThat(entry).isGreaterThan(bootstrap);
      assertThat(doc).contains("parentOrigin: \"http://localhost:" + server.port() + "\"");
      assertThat(doc).contains("sessionId: \"");
      assertThat(captured.toString(StandardCharsets.UTF_8)).contains("Harness ready");
    }
  }

  @Test
  void shellIsAnotherOriginAndFramesTheGameOrigin() throws Exception {
    Path project = Projects.project(tmp, Projects.GOOD_STATE);
    try (HarnessServer server = HarnessLauncher.launch(project, 0, out)) {
      assertThat(get(server, "localhost", "/")).startsWith("HTTP/1.1 302").contains("/?player=");
      String shell = get(server, "localhost", "/?player=Player+1.1");
      assertThat(shell).contains("src=\"http://game.localhost:" + server.port() + "/\"");
      assertThat(shell).doesNotContain("__TURNBASE__");
      assertThat(get(server, "localhost", "/frontend/main.js")).startsWith("HTTP/1.1 404");
      assertThat(get(server, "evil.example", "/")).startsWith("HTTP/1.1 421");
    }
  }

  @Test
  void gameOriginServesAssetsButNotTheManifestBackendOrTraversal() throws Exception {
    Path project = Projects.project(tmp, Projects.GOOD_STATE);
    Files.writeString(project.resolve("harness.json"), "{}");
    try (HarnessServer server = HarnessLauncher.launch(project, 0, out)) {
      String js = get(server, "game.localhost", "/frontend/main.js");
      assertThat(js).startsWith("HTTP/1.1 200").contains("text/javascript").contains("console.log");
      assertThat(get(server, "game.localhost", "/manifest.json")).startsWith("HTTP/1.1 404");
      assertThat(get(server, "game.localhost", "/backend/game.jar")).startsWith("HTTP/1.1 404");
      assertThat(get(server, "game.localhost", "/harness.json")).startsWith("HTTP/1.1 404");
      assertThat(get(server, "game.localhost", "/../../etc/passwd")).doesNotContain("root:");
    }
  }

  @Test
  void fatalFindingRefusesTheLaunchAndBindsNothing() throws Exception {
    Path project = Projects.project(tmp, Projects.GOOD_STATE);
    Files.delete(project.resolve("frontend/main.js"));
    assertThatThrownBy(() -> HarnessLauncher.launch(project, 0, out))
        .isInstanceOf(HarnessLauncher.LaunchRefusedException.class);
    assertThat(captured.toString(StandardCharsets.UTF_8)).contains("ERROR").doesNotContain("ready");
  }

  @Test
  void strictRoundTripMismatchRefusesTheLaunch() throws Exception {
    // Statically clean, so the validator passes; only the executing round trip sees the skew.
    Path project =
        Projects.project(
            tmp,
            """
            package com.example;
            public record CounterState(int count) implements io.turnbase.gdk.GameState {
              public int count() { return count + 1; }
              static CounterState initial() { return new CounterState(0); }
              CounterState next() { return this; }
            }
            """);
    assertThatThrownBy(() -> HarnessLauncher.launch(project, 0, out))
        .isInstanceOf(HarnessLauncher.LaunchRefusedException.class)
        .hasMessageContaining("did not start");
  }

  @Test
  void theFeGdkIsInjectedByImportMapWhenBundled() throws Exception {
    Path project = Projects.project(tmp, Projects.GOOD_STATE);
    try (HarnessServer server = HarnessLauncher.launch(project, 0, out)) {
      String doc = get(server, "game.localhost", "/");
      assertThat(doc).contains("\"@turnbase-platform/gdk\": \"/__turnbase/gdk.js\"");
      assertThat(doc.indexOf("importmap")).isLessThan(doc.indexOf("window.__TURNBASE__"));
      String gdk = get(server, "game.localhost", "/__turnbase/gdk.js");
      // Present after `npm run build`; a Maven-only checkout warns at launch instead.
      assertThat(gdk).startsWith(HarnessServer.gdkAvailable() ? "HTTP/1.1 200" : "HTTP/1.1 404");
      assertThat(captured.toString(StandardCharsets.UTF_8).contains("FE GDK is not bundled"))
          .isEqualTo(!HarnessServer.gdkAvailable());
    }
  }
}
