package io.turnbase.gdk.context;

import io.turnbase.gdk.model.Player;
import java.util.Optional;

/** {@code resync} input — read-only view request for one viewer (FR-IN-006). */
public interface ResyncContext {
  /** Whether the viewer is playing or spectating. */
  ViewerRole role();

  /** The viewing player, or empty for a spectator (who holds no player identity in the session). */
  Optional<Player> viewer();
}
