package io.turnbase.gdk.context;

/** Why a player is confirmed lost (FR-IN-005). */
public enum PlayerLeftReason {
  /** Grace period expired without reconnection ({@code reconnection-flow.md}). */
  GRACE_PERIOD_EXPIRED,
  /** The host kicked the player. */
  KICKED,
  /**
   * Confirmed voluntary departure mid-game; bypasses the grace period (FR-IN-005, {@code
   * reconnection-flow.md} FR-DC-005).
   */
  LEFT
}
