package io.turnbase.serialization;

import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;

/**
 * A Jackson-free description of what {@link GdkObjectMapper#create()} can and cannot bind — the
 * table `bundle-format.md` §4.4's upload-time static analysis reads, so that analysis and this
 * engine cannot drift apart (`game-host-internals.md` §3.6). Contains no reference to Jackson so a
 * caller (the upload validator) can depend on it without pulling Jackson onto a classpath where it
 * does not belong.
 */
public final class BindingCapabilities {

  private static final Set<Class<?>> SUPPORTED_MAP_KEY_TYPES =
      Set.of(
          String.class,
          UUID.class,
          Byte.class,
          Short.class,
          Integer.class,
          Long.class,
          BigInteger.class);

  private static final Set<Class<?>> MODULE_DEPENDENT_TYPES =
      Set.of(
          Instant.class,
          LocalDate.class,
          LocalDateTime.class,
          LocalTime.class,
          Duration.class,
          ZonedDateTime.class,
          Optional.class,
          OptionalInt.class,
          OptionalLong.class,
          OptionalDouble.class);

  private BindingCapabilities() {}

  /**
   * @param keyType a candidate {@code Map} key type
   * @return {@code true} if the configured mapper can round-trip a {@code Map} keyed by {@code
   *     keyType} — {@code String}, an enum, {@code UUID} or a boxed integral type
   *     (`bundle-format.md` §4.4)
   */
  public static boolean supportsMapKey(Class<?> keyType) {
    return keyType.isEnum() || SUPPORTED_MAP_KEY_TYPES.contains(keyType);
  }

  /**
   * @param type a candidate field or component type
   * @return {@code true} if binding {@code type} needs a Jackson module the configured mapper does
   *     not register (e.g. {@code java.time.*}, {@code Optional}) — `bundle-format.md` §4.4
   */
  public static boolean requiresUnregisteredModule(Class<?> type) {
    return MODULE_DEPENDENT_TYPES.contains(type);
  }
}
