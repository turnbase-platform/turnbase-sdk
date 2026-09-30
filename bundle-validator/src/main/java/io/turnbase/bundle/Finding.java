package io.turnbase.bundle;

/**
 * One result of validating a bundle — framework-free, so the platform maps it to its own error
 * shape and the harness prints it. {@code code} names the rule that fired (for example {@code
 * bundle.paths.backend}), {@code message} explains it to the developer.
 *
 * @param severity whether the finding fails the bundle
 * @param code the rule identifier
 * @param message a human-readable explanation
 */
public record Finding(Severity severity, String code, String message) {

  /**
   * @param code the rule identifier
   * @param message a human-readable explanation
   * @return a fatal finding
   */
  public static Finding error(String code, String message) {
    return new Finding(Severity.ERROR, code, message);
  }

  /**
   * @param code the rule identifier
   * @param message a human-readable explanation
   * @return an advisory finding
   */
  public static Finding warning(String code, String message) {
    return new Finding(Severity.WARNING, code, message);
  }
}
