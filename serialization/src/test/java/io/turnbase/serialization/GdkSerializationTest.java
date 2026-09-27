package io.turnbase.serialization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Proves the round-trip engine's contract (§5): copy semantics, size limits, GRMP-F4. */
class GdkSerializationTest {

  private final GdkSerialization serialization = new GdkSerialization();

  record Position(int x, int y) {}

  record BoardState(String turn, List<Position> pieces) {}

  @Test
  void writeStateThenReadStateRecoversAnEquivalentButDistinctObject() {
    var original = new BoardState("north", List.of(new Position(1, 2)));

    String json = serialization.writeState(original, SerializationLimits.DEFAULT_MAX_STATE_BYTES);
    BoardState decoded = serialization.readState(json, BoardState.class);

    assertThat(decoded).isEqualTo(original).isNotSameAs(original);
  }

  @Test
  void writeStateRejectsAStateLargerThanTheLimit() {
    var original = new BoardState("north", List.of(new Position(1, 2)));

    assertThatThrownBy(() -> serialization.writeState(original, 4))
        .isInstanceOf(StateTooLargeException.class)
        .satisfies(
            e -> {
              var too = (StateTooLargeException) e;
              assertThat(too.maxBytes()).isEqualTo(4);
              assertThat(too.actualBytes()).isGreaterThan(4);
            });
  }

  @Test
  void writePayloadRejectsAPayloadLargerThanTheLimit() {
    assertThatThrownBy(() -> serialization.writePayload(new Position(1, 2), 2))
        .isInstanceOf(PayloadTooLargeException.class)
        .satisfies(
            e -> {
              var too = (PayloadTooLargeException) e;
              assertThat(too.maxBytes()).isEqualTo(2);
              assertThat(too.actualBytes()).isGreaterThan(2);
            });
  }

  @Test
  void writePayloadThenReadActionRoundTrips() {
    var action = new Position(3, 4);

    String json = serialization.writePayload(action, SerializationLimits.DEFAULT_MAX_PAYLOAD_BYTES);
    Position decoded = serialization.readAction(json, Position.class);

    assertThat(decoded).isEqualTo(action);
  }

  @Test
  void readStateWrapsAMalformedDocumentInASerializationException() {
    assertThatThrownBy(() -> serialization.readState("not json", BoardState.class))
        .isInstanceOf(SerializationException.class)
        .hasCauseInstanceOf(Exception.class);
  }

  @Test
  void readActionWrapsAMalformedDocumentInASerializationException() {
    assertThatThrownBy(() -> serialization.readAction("{", Position.class))
        .isInstanceOf(SerializationException.class);
  }

  @Test
  void roundTripsIsTrueForAValueThatSurvivesSerializeDeserializeReserialize() {
    assertThat(serialization.roundTrips(new Position(5, 6), Position.class)).isTrue();
  }

  @Test
  void roundTripsIsFalseForATypeTheValueCannotBeReadBackAs() {
    assertThat(serialization.roundTrips(new Position(5, 6), BoardState.class)).isFalse();
  }

  @Test
  void constructorAcceptsAPreConfiguredMapper() {
    var withOwnMapper = new GdkSerialization(GdkObjectMapper.create());

    assertThat(withOwnMapper.roundTrips(new Position(1, 1), Position.class)).isTrue();
  }
}
