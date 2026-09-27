package io.turnbase.gdk.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** {@link ParameterAccessException} must be unchecked (§5 "GameParameters"). */
class ParameterAccessExceptionTest {

  @Test
  void isAnUncheckedException() {
    assertThat(RuntimeException.class).isAssignableFrom(ParameterAccessException.class);
  }

  @Test
  void carriesAMessage() {
    assertThat(new ParameterAccessException("unknown key: maxValue"))
        .hasMessage("unknown key: maxValue");
  }
}
