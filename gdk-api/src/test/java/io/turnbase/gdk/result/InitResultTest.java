package io.turnbase.gdk.result;

import static org.assertj.core.api.Assertions.assertThat;

import io.turnbase.gdk.GameState;
import io.turnbase.gdk.effect.Effects;
import org.junit.jupiter.api.Test;

/** {@link InitResult} mirrors {@link ActionResultTest}'s shape (FR-RESP-002/003/004). */
class InitResultTest {

  private record State(int value) implements GameState {}

  @Test
  void ofProducesAnEmptyEffectList() {
    InitResult<State> result = InitResult.of(new State(1));

    assertThat(result.state()).isEqualTo(new State(1));
    assertThat(result.effects()).isEmpty();
  }

  @Test
  void builderPreservesEffectOrder() {
    InitResult<State> result = InitResult.from(new State(2)).effect(Effects.log("started")).build();

    assertThat(result.effects()).containsExactly(Effects.log("started"));
  }
}
