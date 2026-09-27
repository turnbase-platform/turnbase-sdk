package io.turnbase.serialization;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Proves the capability descriptor matches `bundle-format.md` §4.4's fatal-condition table. */
class BindingCapabilitiesTest {

  enum Suit {
    HEARTS,
    SPADES
  }

  @Test
  void supportsStringUuidEnumAndBoxedIntegralMapKeys() {
    assertThat(BindingCapabilities.supportsMapKey(String.class)).isTrue();
    assertThat(BindingCapabilities.supportsMapKey(UUID.class)).isTrue();
    assertThat(BindingCapabilities.supportsMapKey(Suit.class)).isTrue();
    assertThat(BindingCapabilities.supportsMapKey(Integer.class)).isTrue();
    assertThat(BindingCapabilities.supportsMapKey(BigInteger.class)).isTrue();
  }

  @Test
  void rejectsAnArbitraryTypeAsAMapKey() {
    record Position(int x, int y) {}

    assertThat(BindingCapabilities.supportsMapKey(Position.class)).isFalse();
  }

  @Test
  void flagsJavaTimeAndOptionalAsRequiringAnUnregisteredModule() {
    assertThat(BindingCapabilities.requiresUnregisteredModule(Instant.class)).isTrue();
    assertThat(BindingCapabilities.requiresUnregisteredModule(Optional.class)).isTrue();
  }

  @Test
  void doesNotFlagAnOrdinaryTypeAsRequiringAnUnregisteredModule() {
    assertThat(BindingCapabilities.requiresUnregisteredModule(String.class)).isFalse();
  }
}
