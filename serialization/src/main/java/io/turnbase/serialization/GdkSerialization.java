package io.turnbase.serialization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;

/**
 * The round-trip engine (§5): opaque game state, action payloads and effect payloads cross the
 * Espresso boundary as JSON strings, produced and consumed only here, so the guest bridge, the
 * local test harness and the upload validator apply identical rules (GRMP-F4).
 */
public final class GdkSerialization {

  private final ObjectMapper mapper;

  /** Uses {@link GdkObjectMapper#create()}. */
  public GdkSerialization() {
    this(GdkObjectMapper.create());
  }

  /**
   * @param mapper a pre-configured mapper, for a caller that needs to share or inspect it
   */
  public GdkSerialization(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  /**
   * @param state the developer's state object (a {@code GameState} implementation, opaque to this
   *     engine)
   * @param maxBytes the size ceiling to enforce (see {@link SerializationLimits})
   * @return the serialized state as JSON
   * @throws StateTooLargeException if the serialized form exceeds {@code maxBytes}
   * @throws SerializationException if {@code state} cannot be serialized
   */
  public String writeState(Object state, long maxBytes) {
    byte[] json = writeBytes(state);
    if (json.length > maxBytes) {
      throw new StateTooLargeException(json.length, maxBytes);
    }
    return new String(json, StandardCharsets.UTF_8);
  }

  /**
   * @param json the state JSON previously produced by {@link #writeState(Object, long)}
   * @param type the developer's state type
   * @param <T> the state type
   * @return the deserialized state
   * @throws SerializationException if {@code json} cannot be read as {@code type}
   */
  public <T> T readState(String json, Class<T> type) {
    return read(json, type);
  }

  /**
   * @param payload an opaque, developer-typed effect, {@code resultsPayload} or view payload
   * @param maxBytes the size ceiling to enforce (see {@link SerializationLimits})
   * @return the serialized payload as JSON
   * @throws PayloadTooLargeException if the serialized form exceeds {@code maxBytes}
   * @throws SerializationException if {@code payload} cannot be serialized
   */
  public String writePayload(Object payload, long maxBytes) {
    byte[] json = writeBytes(payload);
    if (json.length > maxBytes) {
      throw new PayloadTooLargeException(json.length, maxBytes);
    }
    return new String(json, StandardCharsets.UTF_8);
  }

  /**
   * @param json an action payload previously serialized by the platform
   * @param type the developer's requested action type ({@code ActionContext#action(Class)})
   * @param <T> the action type
   * @return the decoded action
   * @throws SerializationException if {@code json} cannot be read as {@code type}
   */
  public <T> T readAction(String json, Class<T> type) {
    return read(json, type);
  }

  /**
   * The GRMP-F4 smoke test: serialize, deserialize, re-serialize, and compare the two JSON
   * documents structurally (key order does not matter; byte order does not matter). A value that
   * fails this round trip in the harness or at upload fails identically in production, because this
   * is the same engine that runs there.
   *
   * @param value a candidate state or payload instance
   * @param type the type to deserialize back into
   * @return {@code true} if {@code value} round-trips without loss
   */
  public boolean roundTrips(Object value, Class<?> type) {
    String first;
    try {
      first = mapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      return false;
    }
    Object decoded;
    try {
      decoded = mapper.readValue(first, type);
    } catch (JsonProcessingException e) {
      return false;
    }
    String second;
    try {
      second = mapper.writeValueAsString(decoded);
    } catch (JsonProcessingException e) {
      return false;
    }
    try {
      JsonNode firstTree = mapper.readTree(first);
      JsonNode secondTree = mapper.readTree(second);
      return firstTree.equals(secondTree);
    } catch (JsonProcessingException e) {
      return false;
    }
  }

  private byte[] writeBytes(Object value) {
    try {
      return mapper.writeValueAsBytes(value);
    } catch (JsonProcessingException e) {
      throw new SerializationException("failed to serialize " + value.getClass().getName(), e);
    }
  }

  private <T> T read(String json, Class<T> type) {
    try {
      return mapper.readValue(json, type);
    } catch (JsonProcessingException e) {
      throw new SerializationException("failed to deserialize as " + type.getName(), e);
    }
  }
}
