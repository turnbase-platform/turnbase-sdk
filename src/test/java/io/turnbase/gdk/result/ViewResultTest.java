package io.turnbase.gdk.result;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Proves {@link ViewResult} carries only an opaque view payload (FR-IN-006, FR-RESP-005). */
class ViewResultTest {

  @Test
  void ofWrapsTheGivenView() {
    Object view = new Object();
    assertThat(ViewResult.of(view).view()).isSameAs(view);
  }

  @Test
  void emptyProducesANonNullPlaceholder() {
    assertThat(ViewResult.empty().view()).isNotNull();
  }
}
