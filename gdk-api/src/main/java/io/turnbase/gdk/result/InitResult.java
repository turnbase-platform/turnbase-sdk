package io.turnbase.gdk.result;

import io.turnbase.gdk.GameState;
import io.turnbase.gdk.effect.LogEffect;
import java.util.ArrayList;
import java.util.List;

/**
 * The result of {@code onInit} — the initial state and an ordered list of log entries
 * (FR-RESP-001/002).
 *
 * <p>Unlike the other hooks, {@code onInit} may return only {@link LogEffect}s. Its effects are
 * fanned out at sequence 0, before the game frontend is loaded and before any client has
 * subscribed, and the platform keeps no effect journal; the one thing it retains per session is the
 * game log, which it replays to every client that attaches (FR-DELIVER-003/005). A {@code
 * broadcast}, {@code send_to_player} or {@code send_to_team} returned from here would therefore be
 * accepted and then reach nobody, and {@code game_over} would end a game nobody has seen. The type
 * makes the unreachable cases unrepresentable. Whatever clients must know at game start belongs in
 * the state, which {@code onResync} turns into each viewer's first picture (FR-IN-006, FR-FE-001).
 *
 * @param <S> the game-state type
 * @param state the initial state (FR-RESP-002, FR-STATE-005); mandatory
 * @param effects the ordered log entries (FR-RESP-003/004); may be empty. They are retained and
 *     replayed to every client that attaches, so they are the one kind of effect that survives game
 *     start
 */
public record InitResult<S extends GameState>(S state, List<LogEffect> effects) {

  /**
   * @param state the initial state
   * @param <S> the game-state type
   * @return a result with the given state and no effects (FR-RESP-003)
   */
  public static <S extends GameState> InitResult<S> of(S state) {
    return new InitResult<>(state, List.of());
  }

  /**
   * @param state the (mandatory) initial state
   * @param <S> the game-state type
   * @return a {@link Builder} seeded with {@code state}
   */
  public static <S extends GameState> Builder<S> from(S state) {
    return new Builder<>(state);
  }

  /**
   * Accumulates the ordered effect list for an {@link InitResult}.
   *
   * @param <S> the game-state type
   */
  public static final class Builder<S extends GameState> {
    private final S state;
    private final List<LogEffect> effects = new ArrayList<>();

    private Builder(S state) {
      this.state = state;
    }

    /**
     * @param e the next log entry, appended in call order (FR-RESP-004)
     * @return this builder
     */
    public Builder<S> effect(LogEffect e) {
      effects.add(e);
      return this;
    }

    /**
     * @param es log entries, appended in list order (FR-RESP-004)
     * @return this builder
     */
    public Builder<S> effects(List<LogEffect> es) {
      effects.addAll(es);
      return this;
    }

    /**
     * @return the built {@link InitResult}
     */
    public InitResult<S> build() {
      return new InitResult<>(state, List.copyOf(effects));
    }
  }
}
