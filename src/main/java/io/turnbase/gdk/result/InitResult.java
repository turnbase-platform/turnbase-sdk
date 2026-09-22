package io.turnbase.gdk.result;

import io.turnbase.gdk.GameState;
import io.turnbase.gdk.effect.Effect;
import java.util.ArrayList;
import java.util.List;

/**
 * The result of {@code onInit} — the initial state and an ordered effect list (FR-RESP-001/002).
 *
 * @param <S> the game-state type
 * @param state the initial state (FR-RESP-002, FR-STATE-005); mandatory
 * @param effects the ordered effect list (FR-RESP-003/004); may be empty. Not delivery-guaranteed
 *     to any client — no client has subscribed yet at game start (FR-FE-001)
 */
public record InitResult<S extends GameState>(S state, List<Effect> effects) {

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
    private final List<Effect> effects = new ArrayList<>();

    private Builder(S state) {
      this.state = state;
    }

    /**
     * @param e the next effect, appended in call order (FR-RESP-004)
     * @return this builder
     */
    public Builder<S> effect(Effect e) {
      effects.add(e);
      return this;
    }

    /**
     * @param es effects, appended in list order (FR-RESP-004)
     * @return this builder
     */
    public Builder<S> effects(List<Effect> es) {
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
