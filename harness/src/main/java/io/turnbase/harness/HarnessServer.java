package io.turnbase.harness;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Serves the harness on two origins from one port ({@code test-harness.md} §3): {@code
 * http://localhost:{port}} for the shell and {@code http://game.localhost:{port}} for the game
 * frontend. The two are told apart by the {@code Host} header alone, so the browser treats them as
 * different origins and the postMessage contract of {@code frontend-gdk-api.md} §8 is exercised for
 * real.
 *
 * <p>The game origin's index is the bootstrap document of {@code game-asset-serving.md} §5, with
 * {@code window.__TURNBASE__} populated before the game's JS runs. {@code parentOrigin} is fixed at
 * construction from the shell's origin; nothing in a request can change it.
 */
public final class HarnessServer implements AutoCloseable {

  /** The game origin's host name. */
  public static final String GAME_HOST = "game.localhost";

  private static final String HTML = "text/html; charset=utf-8";

  private static final Map<String, String> TYPES =
      Map.of(
          "html", "text/html; charset=utf-8",
          "js", "text/javascript; charset=utf-8",
          "mjs", "text/javascript; charset=utf-8",
          "css", "text/css; charset=utf-8",
          "json", "application/json",
          "svg", "image/svg+xml",
          "png", "image/png",
          "wasm", "application/wasm");

  private final HttpServer http;
  private final Path root;
  private final String entryPoint;
  private final Set<Path> withheld;
  private final String sessionId = UUID.randomUUID().toString();

  /**
   * @param projectDir the game project directory, the root game assets are served from
   * @param entryPoint the manifest's {@code frontend.entryPoint}, relative to {@code projectDir}
   * @param withheld project files that are never served (manifest, backend JAR, {@code
   *     harness.json}); the game origin is unauthenticated, so not every file may be published
   * @param port the port to bind, or 0 for any free port
   * @throws IOException when the port cannot be bound
   */
  public HarnessServer(Path projectDir, String entryPoint, Set<Path> withheld, int port)
      throws IOException {
    this.root = projectDir.toAbsolutePath().normalize();
    this.entryPoint = entryPoint;
    this.withheld = withheld;
    this.http = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
    http.createContext("/", this::handle);
  }

  /** Starts serving. */
  public void start() {
    http.start();
  }

  /**
   * @return the bound port
   */
  public int port() {
    return http.getAddress().getPort();
  }

  /**
   * @return the shell origin, which is also the game's trusted {@code parentOrigin}
   */
  public String shellOrigin() {
    return "http://localhost:" + port();
  }

  /**
   * @return the game origin
   */
  public String gameOrigin() {
    return "http://" + GAME_HOST + ":" + port();
  }

  @Override
  public void close() {
    http.stop(0);
  }

  private void handle(HttpExchange ex) throws IOException {
    try (ex) {
      if (!ex.getRequestMethod().equals("GET") && !ex.getRequestMethod().equals("HEAD")) {
        send(ex, 405, "text/plain", "method not allowed");
        return;
      }
      String host = ex.getRequestHeaders().getFirst("Host");
      String hostName =
          host == null ? "" : host.replaceFirst(":\\d+$", "").toLowerCase(Locale.ROOT);
      String path = ex.getRequestURI().getPath();
      if (hostName.equals(GAME_HOST)) {
        game(ex, path);
      } else if (hostName.equals("localhost") || hostName.equals("127.0.0.1")) {
        shell(ex, path);
      } else {
        send(ex, 421, "text/plain", "unknown host");
      }
    }
  }

  private void shell(HttpExchange ex, String path) throws IOException {
    if (path.equals("/") || path.equals("/index.html")) {
      send(ex, 200, HTML, shellDocument());
    } else {
      send(ex, 404, "text/plain", "not found");
    }
  }

  private void game(HttpExchange ex, String path) throws IOException {
    if (path.equals("/") || path.equals("/index.html")) {
      send(ex, 200, HTML, bootstrapDocument());
      return;
    }
    Path file = root.resolve(path.substring(1)).normalize();
    if (!file.startsWith(root) || withheld.contains(file) || !Files.isRegularFile(file)) {
      send(ex, 404, "text/plain", "not found");
      return;
    }
    String name = file.getFileName().toString();
    String type =
        TYPES.getOrDefault(name.substring(name.lastIndexOf('.') + 1), "application/octet-stream");
    send(ex, 200, type, Files.readAllBytes(file));
  }

  /**
   * The bootstrap document of {@code game-asset-serving.md} §5, minus the CSP nonce: the harness
   * applies no CSP (§3).
   */
  String bootstrapDocument() {
    return """
        <!doctype html>
        <html>
          <head>
            <meta charset="utf-8">
            <base href="/">
            <script>
              window.__TURNBASE__ = { parentOrigin: %s, sessionId: %s };
            </script>
          </head>
          <body>
            <script type="module" src="%s"></script>
          </body>
        </html>
        """
        .formatted(js(shellOrigin()), js(sessionId), attr(entryPoint));
  }

  private String shellDocument() {
    return """
        <!doctype html>
        <html>
          <head><meta charset="utf-8"><title>TurnBase harness</title></head>
          <body>
            <iframe id="game" src="%s/" style="width:100%%;height:90vh;border:0"
                    sandbox="allow-scripts allow-same-origin"></iframe>
          </body>
        </html>
        """
        .formatted(gameOrigin());
  }

  private static String js(String value) {
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("<", "\\u003c") + "\"";
  }

  private static String attr(String value) {
    return value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;");
  }

  private static void send(HttpExchange ex, int status, String type, String body)
      throws IOException {
    send(ex, status, type, body.getBytes(StandardCharsets.UTF_8));
  }

  private static void send(HttpExchange ex, int status, String type, byte[] body)
      throws IOException {
    ex.getResponseHeaders().set("Content-Type", type);
    ex.getResponseHeaders().set("Cache-Control", "no-store");
    boolean head = ex.getRequestMethod().equals("HEAD");
    ex.sendResponseHeaders(status, head ? -1 : body.length);
    if (!head) {
      try (OutputStream out = ex.getResponseBody()) {
        out.write(body);
      }
    }
  }
}
