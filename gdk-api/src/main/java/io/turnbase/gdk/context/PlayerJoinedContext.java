package io.turnbase.gdk.context;

import io.turnbase.gdk.model.Player;

/** {@code player_joined} input. */
public interface PlayerJoinedContext {
  /** The joining player's information. */
  Player player();
}
