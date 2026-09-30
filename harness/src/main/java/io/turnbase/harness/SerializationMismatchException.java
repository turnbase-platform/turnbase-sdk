package io.turnbase.harness;

/**
 * A game state that did not survive the executing round trip ({@code test-harness.md} §6,
 * FR-HARNESS-010).
 */
public final class SerializationMismatchException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  SerializationMismatchException(String message) {
    super(message);
  }
}
