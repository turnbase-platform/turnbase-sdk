package io.turnbase.harness;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/**
 * A minimal RFC 6455 server: exactly what the shell's STOMP-over-WebSocket client needs (text
 * frames, ping/pong, close, fragmentation) and nothing else. The JDK ships no WebSocket server and
 * a game developer's tool should not pull a servlet container in for one endpoint.
 *
 * <p>The handshake refuses any {@code Origin} the caller does not accept, so a page on another
 * origin — including the game's own — cannot open a session as a player.
 */
public final class WebSocketServer implements AutoCloseable {

  private static final String GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
  private static final int MAX_MESSAGE_BYTES = 1 << 20;

  /** One open connection. */
  public final class Connection {
    private final Socket socket;
    private final OutputStream out;
    private final URI target;
    private volatile Consumer<String> messageHandler = m -> {};
    private volatile Runnable closeHandler = () -> {};
    private volatile boolean open = true;

    Connection(Socket socket, OutputStream out, URI target) {
      this.socket = socket;
      this.out = out;
      this.target = target;
    }

    /**
     * @return the request target's path
     */
    public String path() {
      return target.getPath();
    }

    /**
     * @param name a query parameter
     * @return its decoded value, or {@code null}
     */
    public @Nullable String query(String name) {
      String q = target.getRawQuery();
      if (q == null) {
        return null;
      }
      for (String pair : q.split("&")) {
        int eq = pair.indexOf('=');
        String key = eq < 0 ? pair : pair.substring(0, eq);
        if (java.net.URLDecoder.decode(key, StandardCharsets.UTF_8).equals(name)) {
          return eq < 0
              ? ""
              : java.net.URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
        }
      }
      return null;
    }

    /**
     * @param handler receives each complete text message
     */
    public void onMessage(Consumer<String> handler) {
      this.messageHandler = handler;
    }

    /**
     * @param handler run once when the connection ends, from either side
     */
    public void onClose(Runnable handler) {
      this.closeHandler = handler;
    }

    /**
     * Sends one text message.
     *
     * @param text the message
     */
    public void send(String text) {
      writeFrame(0x1, text.getBytes(StandardCharsets.UTF_8));
    }

    /** Closes the connection with a normal-closure frame. */
    public void close() {
      if (open) {
        writeFrame(0x8, new byte[] {0x03, (byte) 0xE8});
      }
      shutdown();
    }

    private void shutdown() {
      boolean wasOpen;
      synchronized (this) {
        wasOpen = open;
        open = false;
      }
      try {
        socket.close();
      } catch (IOException ignored) {
        // already gone
      }
      if (wasOpen) {
        closeHandler.run();
      }
    }

    private synchronized void writeFrame(int opcode, byte[] payload) {
      if (!open) {
        return;
      }
      try {
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        head.write(0x80 | opcode);
        if (payload.length < 126) {
          head.write(payload.length);
        } else if (payload.length <= 0xFFFF) {
          head.write(126);
          head.write(payload.length >> 8);
          head.write(payload.length);
        } else {
          head.write(127);
          for (int shift = 56; shift >= 0; shift -= 8) {
            head.write((int) ((long) payload.length >> shift));
          }
        }
        out.write(head.toByteArray());
        out.write(payload);
        out.flush();
      } catch (IOException e) {
        open = false;
        try {
          socket.close();
        } catch (IOException ignored) {
          // already gone
        }
      }
    }

    void pump(DataInputStream in) {
      try {
        ByteArrayOutputStream message = new ByteArrayOutputStream();
        boolean text = false;
        while (open) {
          int b0 = in.readUnsignedByte();
          int b1 = in.readUnsignedByte();
          boolean fin = (b0 & 0x80) != 0;
          int opcode = b0 & 0x0F;
          boolean masked = (b1 & 0x80) != 0;
          long len = b1 & 0x7F;
          if (len == 126) {
            len = in.readUnsignedShort();
          } else if (len == 127) {
            len = in.readLong();
          }
          if (!masked || len < 0 || len > MAX_MESSAGE_BYTES) {
            // RFC 6455 §5.1: a client must mask; and an oversized frame is not worth buffering.
            break;
          }
          byte[] mask = new byte[4];
          in.readFully(mask);
          byte[] payload = new byte[(int) len];
          in.readFully(payload);
          for (int i = 0; i < payload.length; i++) {
            payload[i] ^= mask[i & 3];
          }
          switch (opcode) {
            case 0x8 -> {
              writeFrame(0x8, payload);
              return;
            }
            case 0x9 -> writeFrame(0xA, payload);
            case 0xA -> {}
            case 0x1, 0x0 -> {
              if (opcode == 0x1) {
                message.reset();
                text = true;
              }
              if (!text || message.size() + payload.length > MAX_MESSAGE_BYTES) {
                return;
              }
              message.write(payload);
              if (fin) {
                text = false;
                messageHandler.accept(message.toString(StandardCharsets.UTF_8));
              }
            }
            default -> {
              return;
            }
          }
        }
      } catch (EOFException e) {
        // peer went away
      } catch (IOException e) {
        // socket closed under us
      } finally {
        shutdown();
      }
    }
  }

  private final ServerSocket listener;
  private final Predicate<@Nullable String> originAccepted;
  private final Consumer<Connection> onConnect;

  /**
   * @param port the port to bind on loopback, or 0 for any free port
   * @param originAccepted whether a handshake's {@code Origin} header (possibly absent) is allowed
   * @param onConnect called for every accepted connection before its first message is read
   * @throws IOException when the port cannot be bound
   */
  public WebSocketServer(
      int port, Predicate<@Nullable String> originAccepted, Consumer<Connection> onConnect)
      throws IOException {
    this.listener = new ServerSocket(port, 50, InetAddress.getLoopbackAddress());
    this.originAccepted = originAccepted;
    this.onConnect = onConnect;
  }

  /** Starts accepting connections. */
  public void start() {
    Thread.ofPlatform().daemon().name("harness-ws-accept").start(this::acceptLoop);
  }

  /**
   * @return the bound port
   */
  public int port() {
    return listener.getLocalPort();
  }

  @Override
  public void close() {
    try {
      listener.close();
    } catch (IOException ignored) {
      // closing
    }
  }

  private void acceptLoop() {
    while (!listener.isClosed()) {
      try {
        Socket socket = listener.accept();
        Thread.ofVirtual().name("harness-ws").start(() -> serve(socket));
      } catch (IOException e) {
        return;
      }
    }
  }

  private void serve(Socket socket) {
    try {
      socket.setSoTimeout(0);
      InputStream raw = new BufferedInputStream(socket.getInputStream());
      OutputStream out = socket.getOutputStream();
      DataInputStream in = new DataInputStream(raw);
      Map<String, String> headers = new HashMap<>();
      String requestLine = readLine(in);
      String line;
      while (!(line = readLine(in)).isEmpty()) {
        int colon = line.indexOf(':');
        if (colon > 0) {
          headers.put(
              line.substring(0, colon).trim().toLowerCase(Locale.ROOT),
              line.substring(colon + 1).trim());
        }
      }
      String[] parts = requestLine.split(" ");
      String key = headers.get("sec-websocket-key");
      if (parts.length < 2
          || !parts[0].equals("GET")
          || key == null
          || !"websocket".equalsIgnoreCase(headers.getOrDefault("upgrade", ""))) {
        reject(out, "400 Bad Request");
        socket.close();
        return;
      }
      if (!originAccepted.test(headers.get("origin"))) {
        reject(out, "403 Forbidden");
        socket.close();
        return;
      }
      URI target = URI.create(parts[1]);
      out.write(
          ("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                  + "Sec-WebSocket-Accept: "
                  + accept(key)
                  + "\r\n\r\n")
              .getBytes(StandardCharsets.US_ASCII));
      out.flush();
      Connection connection = new Connection(socket, out, target);
      onConnect.accept(connection);
      connection.pump(in);
    } catch (IOException | RuntimeException e) {
      try {
        socket.close();
      } catch (IOException ignored) {
        // already gone
      }
    }
  }

  static String accept(String key) {
    try {
      MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
      return Base64.getEncoder()
          .encodeToString(sha1.digest((key + GUID).getBytes(StandardCharsets.US_ASCII)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static void reject(OutputStream out, String status) throws IOException {
    out.write(
        ("HTTP/1.1 " + status + "\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
            .getBytes(StandardCharsets.US_ASCII));
    out.flush();
  }

  private static String readLine(DataInputStream in) throws IOException {
    StringBuilder sb = new StringBuilder();
    int c;
    while ((c = in.read()) != -1) {
      if (c == '\n') {
        return sb.toString().replaceAll("\r$", "");
      }
      if (sb.length() > 8192) {
        throw new IOException("header line too long");
      }
      sb.append((char) c);
    }
    throw new EOFException();
  }
}
