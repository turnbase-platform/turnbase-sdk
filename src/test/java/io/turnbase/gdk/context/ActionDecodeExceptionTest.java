package io.turnbase.gdk.context;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** {@link ActionDecodeException} must be unchecked (§4 "Action typing"). */
class ActionDecodeExceptionTest {

  @Test
  void isAnUncheckedException() {
    assertThat(RuntimeException.class).isAssignableFrom(ActionDecodeException.class);
  }

  @Test
  void carriesAMessageAndOptionalCause() {
    var cause = new IllegalStateException("bad json");
    var withCause = new ActionDecodeException("could not decode", cause);
    var withoutCause = new ActionDecodeException("could not decode");

    assertThat(withCause).hasMessage("could not decode").hasCause(cause);
    assertThat(withoutCause).hasMessage("could not decode").hasNoCause();
  }
}
