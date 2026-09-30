package io.turnbase.harness;

import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** STOMP 1.2 frames, as much as a browser client sends and receives over a WebSocket. */
final class Stomp {

  private Stomp() {}

  /**
   * One frame.
   *
   * @param command {@code CONNECT}, {@code SUBSCRIBE}, {@code MESSAGE}, …
   * @param headers the headers, first occurrence wins per the spec
   * @param body the body
   */
  record Frame(String command, Map<String, String> headers, String body) {
    @Nullable String header(String name) {
      return headers.get(name);
    }
  }

  /**
   * @param text one WebSocket text message
   * @return the frame, or {@code null} for a heart-beat (blank line) or garbage
   */
  static @Nullable Frame parse(String text) {
    String s = text.replace("\r\n", "\n");
    int start = 0;
    while (start < s.length() && s.charAt(start) == '\n') {
      start++;
    }
    if (start >= s.length()) {
      return null;
    }
    int headerEnd = s.indexOf("\n\n", start);
    if (headerEnd < 0) {
      return null;
    }
    String[] lines = s.substring(start, headerEnd).split("\n");
    Map<String, String> headers = new LinkedHashMap<>();
    for (int i = 1; i < lines.length; i++) {
      int colon = lines[i].indexOf(':');
      if (colon > 0) {
        headers.putIfAbsent(
            unescape(lines[i].substring(0, colon)), unescape(lines[i].substring(colon + 1)));
      }
    }
    String body = s.substring(headerEnd + 2);
    int nul = body.indexOf('\0');
    if (nul >= 0) {
      body = body.substring(0, nul);
    }
    return new Frame(lines[0], headers, body);
  }

  static String format(String command, Map<String, String> headers, String body) {
    StringBuilder sb = new StringBuilder(command).append('\n');
    headers.forEach((k, v) -> sb.append(escape(k)).append(':').append(escape(v)).append('\n'));
    return sb.append('\n').append(body).append('\0').toString();
  }

  private static String escape(String v) {
    return v.replace("\\", "\\\\").replace("\n", "\\n").replace(":", "\\c").replace("\r", "\\r");
  }

  private static String unescape(String v) {
    if (v.indexOf('\\') < 0) {
      return v;
    }
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < v.length(); i++) {
      char c = v.charAt(i);
      if (c == '\\' && i + 1 < v.length()) {
        char n = v.charAt(++i);
        sb.append(
            switch (n) {
              case 'n' -> '\n';
              case 'c' -> ':';
              case 'r' -> '\r';
              default -> n;
            });
      } else {
        sb.append(c);
      }
    }
    return sb.toString();
  }
}
