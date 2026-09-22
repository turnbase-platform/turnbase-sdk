package io.turnbase.gdk;

import io.turnbase.gdk.context.ActionContext;
import io.turnbase.gdk.context.InitContext;
import io.turnbase.gdk.context.PlayerJoinedContext;
import io.turnbase.gdk.context.PlayerLeftContext;
import io.turnbase.gdk.context.ResyncContext;
import io.turnbase.gdk.result.ActionResult;
import io.turnbase.gdk.result.InitResult;
import io.turnbase.gdk.result.PlayerJoinedResult;
import io.turnbase.gdk.result.PlayerLeftResult;
import io.turnbase.gdk.result.ViewResult;

/**
 * A TurnBase game backend. Implemented by game developers; instantiated and invoked by the GDK
 * Host. Implementations must be deterministic functions of their inputs (see {@code
 * backend-gdk-api.md} §9) and hold no mutable static state.
 *
 * @param <S> the game-state type, a JSON-serializable type implementing {@link GameState}
 */
public interface TurnBaseGame<S extends GameState> {

  /**
   * Game start. No prior state exists; the result establishes the initial state (FR-IN-002,
   * FR-STATE-005).
   */
  InitResult<S> onInit(InitContext ctx);

  /** A player submitted an action (FR-IN-003). */
  ActionResult<S> onAction(S state, ActionContext ctx);

  /**
   * A player joined mid-session (FR-IN-004, Should). Default: state unchanged, no effects. Override
   * to react to late joins.
   */
  default PlayerJoinedResult<S> onPlayerJoined(S state, PlayerJoinedContext ctx) {
    return PlayerJoinedResult.of(state);
  }

  /**
   * A player was confirmed lost — grace period expired or host kick (FR-IN-005). Never invoked
   * during the grace period (CON-007).
   */
  PlayerLeftResult<S> onPlayerLeft(S state, PlayerLeftContext ctx);

  /**
   * Compose this viewer's current picture of the game (FR-IN-006).
   *
   * <p>Invoked every time a client attaches to the session: at game start, on reconnection, and on
   * mid-game join. The returned view is delivered to that viewer alone, and is the first {@code
   * game_event} their frontend receives.
   *
   * <p><b>Read-only.</b> Returns a view payload — no state, no effects. The platform persists
   * nothing from it. Return exactly what this viewer is entitled to see: their own hidden
   * information plus the public position for a player, and the public position alone for a
   * spectator ({@code ctx.role()}), because the platform cannot filter an opaque payload on the
   * game's behalf (NFR-SEC-002).
   *
   * <p>Initial state must be established here rather than in {@code onInit}'s effects: at game
   * start no client has subscribed yet, so {@code onInit} effects are not delivery-guaranteed
   * (FR-FE-001).
   */
  ViewResult onResync(S state, ResyncContext ctx);
}
