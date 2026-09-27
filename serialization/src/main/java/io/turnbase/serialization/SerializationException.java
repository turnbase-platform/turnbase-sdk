package io.turnbase.serialization;

/**
 * A value could not be serialized to JSON, or JSON could not be deserialized to the requested type
 * — Jackson's own failure, wrapped so callers depend on this engine's exception, not Jackson's
 * (§5's "unserializable" case, mapped to a wire failure kind by the caller).
 */
public class SerializationException extends RuntimeException {

  /**
   * @param message description of the failure
   * @param cause the underlying Jackson failure
   */
  public SerializationException(String message, Throwable cause) {
    super(message, cause);
  }
}
