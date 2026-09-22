package io.turnbase.gdk.result;

import static org.assertj.core.api.Assertions.assertThat;

import io.turnbase.gdk.GameState;
import io.turnbase.gdk.effect.Effects;
import org.junit.jupiter.api.Test;

/** {@link PlayerLeftResult} mirrors {@link ActionResultTest}'s shape (FR-RESP-002/003/004). */
class PlayerLeftResultTest {

  private record State(int value) implements GameState {}

  @Test
  void ofProducesAnEmptyEffectList() {
    PlayerLeftResult<State> result = PlayerLeftResult.of(new State(1));

    assertThat(result.state()).isEqualTo(new State(1));
    assertThat(result.effects()).isEmpty();
  }

  @Test
  void builderPreservesEffectOrder() {
    PlayerLeftResult<State> result =
        PlayerLeftResult.from(new State(2)).effect(Effects.log("left")).build();

    assertThat(result.effects()).containsExactly(Effects.log("left"));
  }
}
