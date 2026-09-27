package io.turnbase.gdk.result;

import static org.assertj.core.api.Assertions.assertThat;

import io.turnbase.gdk.GameState;
import io.turnbase.gdk.effect.Effects;
import org.junit.jupiter.api.Test;

/** {@link PlayerJoinedResult} mirrors {@link ActionResultTest}'s shape (FR-RESP-002/003/004). */
class PlayerJoinedResultTest {

  private record State(int value) implements GameState {}

  @Test
  void ofProducesAnEmptyEffectList() {
    PlayerJoinedResult<State> result = PlayerJoinedResult.of(new State(1));

    assertThat(result.state()).isEqualTo(new State(1));
    assertThat(result.effects()).isEmpty();
  }

  @Test
  void builderPreservesEffectOrder() {
    PlayerJoinedResult<State> result =
        PlayerJoinedResult.from(new State(2)).effect(Effects.log("joined")).build();

    assertThat(result.effects()).containsExactly(Effects.log("joined"));
  }

  @Test
  void defaultOnPlayerJoinedInTurnBaseGameReturnsStateUnchangedWithNoEffects() {
    class DefaultingGame implements io.turnbase.gdk.TurnBaseGame<State> {
      @Override
      public InitResult<State> onInit(io.turnbase.gdk.context.InitContext ctx) {
        throw new UnsupportedOperationException();
      }

      @Override
      public ActionResult<State> onAction(State state, io.turnbase.gdk.context.ActionContext ctx) {
        throw new UnsupportedOperationException();
      }

      @Override
      public PlayerLeftResult<State> onPlayerLeft(
          State state, io.turnbase.gdk.context.PlayerLeftContext ctx) {
        throw new UnsupportedOperationException();
      }

      @Override
      public ViewResult onResync(State state, io.turnbase.gdk.context.ResyncContext ctx) {
        throw new UnsupportedOperationException();
      }
    }

    io.turnbase.gdk.model.Player player =
        new io.turnbase.gdk.model.Player(
            java.util.UUID.randomUUID(), "Alice", java.util.UUID.randomUUID());
    io.turnbase.gdk.context.PlayerJoinedContext ctx = () -> player;

    State state = new State(7);
    PlayerJoinedResult<State> result = new DefaultingGame().onPlayerJoined(state, ctx);

    assertThat(result.state()).isEqualTo(state);
    assertThat(result.effects()).isEmpty();
  }
}
