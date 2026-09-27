package io.turnbase.serialization;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;

/** Proves {@link GdkObjectMapper#create()}'s configuration, independent of the engine using it. */
class GdkObjectMapperTest {

  @Test
  void bindsAConstructorParameterByNameWithoutJsonPropertyAnnotations() {
    record NoAnnotations(String name, int count) {}

    var mapper = GdkObjectMapper.create();

    assertThat(mapper.canDeserialize(mapper.constructType(NoAnnotations.class))).isTrue();
  }

  @Test
  void toleratesUnknownProperties() {
    assertThat(
            GdkObjectMapper.create().isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES))
        .isFalse();
  }

  @Test
  void toleratesEmptyBeans() {
    assertThat(GdkObjectMapper.create().isEnabled(SerializationFeature.FAIL_ON_EMPTY_BEANS))
        .isFalse();
  }

  @Test
  void createReturnsAFreshInstanceEachCall() {
    assertThat(GdkObjectMapper.create()).isNotSameAs(GdkObjectMapper.create());
  }
}
