package io.turnbase.gdk.model;

/**
 * Thrown by a {@link GameParameters} getter when the requested key is not a declared parameter, or
 * is declared with a different type than the getter called. Unchecked: throwing it (or any
 * exception) from a hook surfaces as {@code GAME_FAILURE_KIND_HANDLER_EXCEPTION} on the wire
 * ({@code backend-gdk-api.md} §9).
 */
public class ParameterAccessException extends RuntimeException {

  /**
   * @param message description of the access failure
   */
  public ParameterAccessException(String message) {
    super(message);
  }
}
