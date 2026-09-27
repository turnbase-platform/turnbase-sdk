package io.turnbase.serialization;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StateTooLargeExceptionTest {

  @Test
  void carriesTheOffendingAndLimitSizes() {
    var exception = new StateTooLargeException(11, 10);

    assertThat(exception.actualBytes()).isEqualTo(11);
    assertThat(exception.maxBytes()).isEqualTo(10);
    assertThat(exception).hasMessageContaining("11").hasMessageContaining("10");
  }
}
