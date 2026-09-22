package io.turnbase.gdk.context;

/** Whether a {@link ResyncContext} request is for a player or a spectator. */
public enum ViewerRole {
  /** The viewer holds a player identity in the session. */
  PLAYER,
  /** The viewer is spectating and holds no player identity in the session. */
  SPECTATOR
}
