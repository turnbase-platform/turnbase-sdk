package io.turnbase.gdk.context;

/**
 * Thrown by {@link ActionContext#action(Class)} when the opaque action payload cannot be
 * deserialized as the requested type. Unchecked: throwing it (or any exception) from a hook
 * surfaces as {@code GAME_FAILURE_KIND_HANDLER_EXCEPTION} on the wire ({@code backend-gdk-api.md}
 * §9).
 */
public class ActionDecodeException extends RuntimeException {

  /**
   * @param message description of the decode failure
   */
  public ActionDecodeException(String message) {
    super(message);
  }

  /**
   * @param message description of the decode failure
   * @param cause the underlying deserialization failure
   */
  public ActionDecodeException(String message, Throwable cause) {
    super(message, cause);
  }
}
