package io.turnbase.serialization;

/**
 * Serialized game state exceeded its byte limit (NFR-SIZE-001), mapped by the guest bridge to
 * {@code GAME_FAILURE_KIND_STATE_TOO_LARGE} (game-host-internals.md §5).
 */
public class StateTooLargeException extends RuntimeException {

  private final long actualBytes;
  private final long maxBytes;

  /**
   * @param actualBytes the serialized size that was rejected
   * @param maxBytes the limit it exceeded
   */
  public StateTooLargeException(long actualBytes, long maxBytes) {
    super(
        "serialized state is " + actualBytes + " bytes, exceeding the " + maxBytes + "-byte limit");
    this.actualBytes = actualBytes;
    this.maxBytes = maxBytes;
  }

  /**
   * @return the serialized size that was rejected
   */
  public long actualBytes() {
    return actualBytes;
  }

  /**
   * @return the limit it exceeded
   */
  public long maxBytes() {
    return maxBytes;
  }
}
