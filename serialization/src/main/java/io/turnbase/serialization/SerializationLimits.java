package io.turnbase.serialization;

/**
 * The NFR-SIZE-001/002 default byte ceilings ("10 MB (configurable)", {@code game-contracts.md}).
 * Defaults only — every {@link GdkSerialization} method that checks a limit takes it as a
 * parameter, so a caller (the guest bridge today; a future per-environment override) is free to
 * configure a different one without a change here.
 */
public final class SerializationLimits {

  /** NFR-SIZE-001: maximum serialized game state size per session. */
  public static final long DEFAULT_MAX_STATE_BYTES = 10L * 1024 * 1024;

  /** NFR-SIZE-002: maximum individual effect (or view) payload size. */
  public static final long DEFAULT_MAX_PAYLOAD_BYTES = 10L * 1024 * 1024;

  private SerializationLimits() {}
}
