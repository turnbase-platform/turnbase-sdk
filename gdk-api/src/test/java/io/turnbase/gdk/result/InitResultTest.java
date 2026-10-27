package io.turnbase.gdk.result;

import static org.assertj.core.api.Assertions.assertThat;

import io.turnbase.gdk.GameState;
import io.turnbase.gdk.effect.Effects;
import java.util.List;
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

  @Test
  void builderAcceptsAListOfLogEntriesInOrder() {
    InitResult<State> result =
        InitResult.from(new State(3))
            .effects(List.of(Effects.log("one"), Effects.log("two")))
            .effect(Effects.log("three"))
            .build();

    assertThat(result.effects())
        .containsExactly(Effects.log("one"), Effects.log("two"), Effects.log("three"));
  }

  /** The restriction is the type itself: a client-facing effect does not compile (see Javadoc). */
  @Test
  void effectsAreTypedAsLogEntries() throws NoSuchMethodException {
    var accessor = InitResult.class.getMethod("effects");

    assertThat(accessor.getGenericReturnType().getTypeName())
        .isEqualTo("java.util.List<io.turnbase.gdk.effect.LogEffect>");
  }
}
