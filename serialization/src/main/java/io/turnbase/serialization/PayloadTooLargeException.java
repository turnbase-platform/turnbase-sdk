package io.turnbase.serialization;

/**
 * A serialized effect, {@code resultsPayload} or view payload exceeded its byte limit
 * (NFR-SIZE-002), mapped by the guest bridge to {@code GAME_FAILURE_KIND_MALFORMED_OUTPUT}
 * (game-host-internals.md §5 — an oversized payload is classified as malformed output, not its own
 * failure kind).
 */
public class PayloadTooLargeException extends RuntimeException {

  private final long actualBytes;
  private final long maxBytes;

  /**
   * @param actualBytes the serialized size that was rejected
   * @param maxBytes the limit it exceeded
   */
  public PayloadTooLargeException(long actualBytes, long maxBytes) {
    super(
        "serialized payload is "
            + actualBytes
            + " bytes, exceeding the "
            + maxBytes
            + "-byte limit");
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
