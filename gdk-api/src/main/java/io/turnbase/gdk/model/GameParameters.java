package io.turnbase.gdk.model;

import java.util.Set;

/**
 * Typed, read-only access to host-configured parameters (FR-GDK-011). The accessor set mirrors the
 * manifest parameter types (FR-MANIFEST-005): {@code integer}, {@code decimal}, {@code boolean},
 * {@code string}, {@code enum}. Parameters always resolve to a value (the manifest declares a
 * default for every parameter), so the required getters never return absent.
 */
public interface GameParameters {
  /**
   * @param key a declared {@code integer} parameter
   * @return the parameter's configured value
   * @throws ParameterAccessException if {@code key} is not declared or is not an {@code integer}
   */
  int getInt(String key);

  /**
   * @param key a declared {@code decimal} parameter
   * @return the parameter's configured value
   * @throws ParameterAccessException if {@code key} is not declared or is not a {@code decimal}
   */
  double getDecimal(String key);

  /**
   * @param key a declared {@code boolean} parameter
   * @return the parameter's configured value
   * @throws ParameterAccessException if {@code key} is not declared or is not a {@code boolean}
   */
  boolean getBoolean(String key);

  /**
   * @param key a declared {@code string} parameter
   * @return the parameter's configured value
   * @throws ParameterAccessException if {@code key} is not declared or is not a {@code string}
   */
  String getString(String key);

  /**
   * @param key a declared {@code enum} parameter
   * @return the selected enum option, as its declared option string
   * @throws ParameterAccessException if {@code key} is not declared or is not an {@code enum}
   */
  String getEnum(String key);

  /**
   * @param key a parameter key
   * @return {@code true} if {@code key} is a declared parameter
   */
  boolean contains(String key);

  /**
   * @return all declared parameter keys
   */
  Set<String> keys();
}
