package io.turnbase.gdk.result;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.turnbase.gdk.GameState;
import io.turnbase.gdk.effect.Effects;
import org.junit.jupiter.api.Test;

/**
 * Proves the shared result shape (FR-RESP-002/003/004) for {@link ActionResult}. {@link
 * InitResultTest}, {@link PlayerJoinedResultTest} and {@link PlayerLeftResultTest} mirror this for
 * the other three result types, per {@code backend-gdk-api.md} §7's "identical in form" note.
 */
class ActionResultTest {

  private record State(int value) implements GameState {}

  @Test
  void ofProducesAnEmptyEffectList() {
    ActionResult<State> result = ActionResult.of(new State(1));

    assertThat(result.state()).isEqualTo(new State(1));
    assertThat(result.effects()).isEmpty();
  }

  @Test
  void builderPreservesEffectOrder() {
    ActionResult<State> result =
        ActionResult.from(new State(2))
            .effect(Effects.log("first"))
            .effect(Effects.broadcast("second"))
            .build();

    assertThat(result.state()).isEqualTo(new State(2));
    assertThat(result.effects()).hasSize(2);
    assertThat(result.effects().get(0)).isEqualTo(Effects.log("first"));
    assertThat(result.effects().get(1)).isEqualTo(Effects.broadcast("second"));
  }

  @Test
  void builderEffectsBulkAppendsInListOrder() {
    ActionResult<State> result =
        ActionResult.from(new State(3))
            .effects(java.util.List.of(Effects.log("a"), Effects.log("b")))
            .effect(Effects.log("c"))
            .build();

    assertThat(result.effects())
        .containsExactly(Effects.log("a"), Effects.log("b"), Effects.log("c"));
  }

  @Test
  void builtEffectListIsImmutable() {
    ActionResult<State> result = ActionResult.from(new State(4)).build();

    assertThatThrownBy(() -> result.effects().add(Effects.log("x")))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
