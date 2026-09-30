package io.turnbase.harness;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

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
  private static final String JS = "text/javascript; charset=utf-8";
  private static final String CSS = "text/css; charset=utf-8";
  private static final String JSON_TYPE = "application/json";

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
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final String GDK_RESOURCE = "/io/turnbase/harness/gdk/index.js";
  private static final String GDK_PATH = "/__turnbase/gdk.js";

  private final MockOrchestration orchestration;
  private final Map<String, JsonNode> parameters;
  private final Consumer<String> log;
  private final String sessionId;
  private @Nullable WebSocketServer sockets;

  /**
   * @param projectDir the game project directory, the root game assets are served from
   * @param entryPoint the manifest's {@code frontend.entryPoint}, relative to {@code projectDir}
   * @param withheld project files that are never served (manifest, backend JAR, {@code
   *     harness.json}); the game origin is unauthenticated, so not every file may be published
   * @param port the port to bind, or 0 for any free port
   * @param orchestration the mock Orchestration the shell's WebSocket connects to
   * @param parameters the effective game parameters, shown in the shell's panel
   * @param log receives one line per rejected request
   * @throws IOException when the port cannot be bound
   */
  public HarnessServer(
      Path projectDir,
      String entryPoint,
      Set<Path> withheld,
      int port,
      MockOrchestration orchestration,
      Map<String, JsonNode> parameters,
      Consumer<String> log)
      throws IOException {
    this.orchestration = orchestration;
    this.parameters = parameters;
    this.log = log;
    this.sessionId = orchestration.sessionId();
    this.root = projectDir.toAbsolutePath().normalize();
    this.entryPoint = entryPoint;
    this.withheld = withheld;
    this.http = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
    http.createContext("/", this::handle);
  }

  /**
   * Starts serving HTTP and the shell's WebSocket endpoint.
   *
   * @throws IOException when the WebSocket port cannot be bound
   */
  public void start() throws IOException {
    WebSocketServer ws =
        new WebSocketServer(
            0,
            // Only the shell may open a player's connection; the game origin must not be able to
            // act as one (FR-FA-002).
            origin -> shellOrigin().equals(origin),
            c -> {
              MockOrchestration.StompSession session =
                  orchestration.connect(
                      c.query("player"),
                      new MockOrchestration.Link() {
                        @Override
                        public void send(String text) {
                          c.send(text);
                        }

                        @Override
                        public void close() {
                          c.close();
                        }
                      });
              c.onMessage(session::receive);
              c.onClose(session::closed);
            });
    ws.start();
    sockets = ws;
    http.start();
  }

  /**
   * @return the port of the WebSocket endpoint the shell connects to
   */
  public int webSocketPort() {
    WebSocketServer ws = sockets;
    if (ws == null) {
      throw new IllegalStateException("not started");
    }
    return ws.port();
  }

  /**
   * @return whether the FE GDK is bundled with the harness and so can be injected
   */
  public static boolean gdkAvailable() {
    return HarnessServer.class.getResource(GDK_RESOURCE) != null;
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
    WebSocketServer ws = sockets;
    if (ws != null) {
      ws.close();
    }
  }

  private void handle(HttpExchange ex) throws IOException {
    try (ex) {
      String host = ex.getRequestHeaders().getFirst("Host");
      String hostName =
          host == null ? "" : host.replaceFirst(":\\d+$", "").toLowerCase(Locale.ROOT);
      String path = ex.getRequestURI().getPath();
      boolean read = ex.getRequestMethod().equals("GET") || ex.getRequestMethod().equals("HEAD");
      if (hostName.equals(GAME_HOST)) {
        if (!read) {
          send(ex, 405, "text/plain", "method not allowed");
        } else {
          game(ex, path);
        }
      } else if (hostName.equals("localhost") || hostName.equals("127.0.0.1")) {
        shell(ex, path, read);
      } else {
        send(ex, 421, "text/plain", "unknown host");
      }
    }
  }

  private void shell(HttpExchange ex, String path, boolean read) throws IOException {
    if (path.equals("/__harness/debug")) {
      debug(ex);
      return;
    }
    if (!read) {
      send(ex, 405, "text/plain", "method not allowed");
      return;
    }
    switch (path) {
      case "/", "/index.html" -> {
        String player = queryParam(ex, "player");
        String canonical = player == null ? null : orchestration.canonicalName(player);
        if (canonical == null) {
          String first = orchestration.participantNames().get(0);
          ex.getResponseHeaders()
              .set(
                  "Location",
                  "/?player=" + java.net.URLEncoder.encode(first, StandardCharsets.UTF_8));
          send(ex, 302, "text/plain", "");
        } else {
          send(ex, 200, HTML, shellDocument(canonical));
        }
      }
      case "/shell.js" -> send(ex, 200, JS, resource("shell.js"));
      case "/shell.css" -> send(ex, 200, CSS, resource("shell.css"));
      case "/__harness/roster" -> {
        ObjectNode roster = orchestration.describe();
        roster.putArray("participants");
        orchestration
            .participantNames()
            .forEach(
                n ->
                    ((com.fasterxml.jackson.databind.node.ArrayNode) roster.get("participants"))
                        .add(n));
        ObjectNode params = roster.putObject("parameters");
        parameters.forEach(params::set);
        send(ex, 200, JSON_TYPE, roster.toString());
      }
      default -> send(ex, 404, "text/plain", "not found");
    }
  }

  /**
   * The debug panel's endpoint: one {@link DebugCommand} as JSON. A custom header is required,
   * which makes a cross-origin request preflighted, and the harness answers no preflight — so the
   * game origin cannot drive it, whatever it sends.
   */
  private void debug(HttpExchange ex) throws IOException {
    String origin = ex.getRequestHeaders().getFirst("Origin");
    if (!ex.getRequestMethod().equals("POST")
        || ex.getRequestHeaders().getFirst("X-Harness") == null
        || (origin != null && !origin.equals(shellOrigin()))) {
      log.accept("refused a debug request (origin " + origin + ")");
      send(ex, 403, "text/plain", "forbidden");
      return;
    }
    try {
      DebugCommand.fromJson(JSON.readTree(ex.getRequestBody().readNBytes(1 << 20)))
          .applyTo(orchestration);
      send(ex, 200, "text/plain", "ok");
    } catch (IllegalArgumentException | SerializationMismatchException e) {
      send(ex, 400, "text/plain", String.valueOf(e.getMessage()));
    }
  }

  private static @Nullable String queryParam(HttpExchange ex, String name) {
    String q = ex.getRequestURI().getRawQuery();
    if (q == null) {
      return null;
    }
    for (String pair : q.split("&")) {
      int eq = pair.indexOf('=');
      if (eq > 0
          && java.net.URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8)
              .equals(name)) {
        return java.net.URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
      }
    }
    return null;
  }

  private static String resource(String name) throws IOException {
    try (var in = HarnessServer.class.getResourceAsStream("/io/turnbase/harness/" + name)) {
      if (in == null) {
        throw new IOException("missing harness resource " + name);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private void game(HttpExchange ex, String path) throws IOException {
    if (path.equals("/") || path.equals("/index.html")) {
      send(ex, 200, HTML, bootstrapDocument());
      return;
    }
    if (path.equals(GDK_PATH)) {
      try (var in = HarnessServer.class.getResourceAsStream(GDK_RESOURCE)) {
        if (in == null) {
          send(ex, 404, "text/plain", "the FE GDK is not bundled with this harness build");
        } else {
          send(ex, 200, JS, in.readAllBytes());
        }
      }
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
            <script type="importmap">
              { "imports": { "@turnbase/gdk": "%s" } }
            </script>
            <script>
              window.__TURNBASE__ = { parentOrigin: %s, sessionId: %s };
            </script>
          </head>
          <body>
            <script type="module" src="%s"></script>
          </body>
        </html>
        """
        .formatted(GDK_PATH, js(shellOrigin()), js(sessionId), attr(entryPoint));
  }

  private String shellDocument(String player) throws IOException {
    ObjectNode config = JSON.createObjectNode();
    config.put("player", player);
    config.put("sessionId", sessionId);
    config.put("gameOrigin", gameOrigin());
    config.put("wsPort", webSocketPort());
    String teamId = orchestration.teamIdOf(player);
    if (teamId != null) {
      config.put("teamId", teamId);
    }
    return resource("shell.html")
        .replace("{{CONFIG}}", config.toString().replace("<", "\\u003c"))
        .replace("{{GAME_ORIGIN}}", attr(gameOrigin()))
        .replace("{{PLAYER}}", attr(player));
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
