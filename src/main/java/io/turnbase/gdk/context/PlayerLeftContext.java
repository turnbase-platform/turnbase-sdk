package io.turnbase.gdk.context;

import io.turnbase.gdk.model.Player;

/** {@code player_left} input. */
public interface PlayerLeftContext {
  /** The departing player's information. */
  Player player();

  /** Why the player is confirmed lost. */
  PlayerLeftReason reason();
}
