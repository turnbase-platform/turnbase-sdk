package io.turnbase.serialization;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;

/**
 * Builds the one {@code ObjectMapper} configuration this engine runs everywhere (§5: "the exact
 * ObjectMapper configuration ... live[s] in one module"). {@code ObjectMapper} is thread-safe once
 * configured, so callers share the instance returned by {@link #create()} rather than building
 * their own.
 */
public final class GdkObjectMapper {

  private GdkObjectMapper() {}

  /**
   * @return a new, fully configured {@code ObjectMapper} for GDK state, action and effect payload
   *     JSON (CON-004, FR-GDK-013)
   */
  public static ObjectMapper create() {
    ObjectMapper mapper = new ObjectMapper();

    // backend-gdk-api.md §5.1 allows "a record or a class compiled with -parameters" with no
    // developer-written serialization code and no @JsonProperty annotations. Records bind
    // natively from jackson-databind 2.12 on; this module resolves the plain-class half of that
    // rule by reading constructor parameter names instead of requiring annotations for them too.
    mapper.registerModule(new ParameterNamesModule());

    // A state or payload type with zero visible properties (e.g. a marker record) is valid
    // GDK state (GameState carries no methods) and must round-trip to "{}", not fail to
    // serialize at all.
    mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

    // Forward-compatible by default: a game evolving its own state/payload shape across
    // versions is the game's concern, not a reason for the shared engine to reject a field it
    // does not recognize.
    mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    return mapper;
  }
}
