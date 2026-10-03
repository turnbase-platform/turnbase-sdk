package io.turnbase.serialization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
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

  record Cell(Integer cell) {}

  @Test
  void rejectsAStringWhereAnIntegerIsExpected() {
    var mapper = GdkObjectMapper.create();

    assertThatThrownBy(() -> mapper.readValue("{\"cell\":\"4\"}", Cell.class))
        .isInstanceOf(JsonMappingException.class);
  }

  @Test
  void stillBindsAnIntegerAndNull() throws Exception {
    var mapper = GdkObjectMapper.create();

    assertThat(mapper.readValue("{\"cell\":4}", Cell.class).cell()).isEqualTo(4);
    assertThat(mapper.readValue("{\"cell\":null}", Cell.class).cell()).isNull();
  }

  @Test
  void createReturnsAFreshInstanceEachCall() {
    assertThat(GdkObjectMapper.create()).isNotSameAs(GdkObjectMapper.create());
  }
}
