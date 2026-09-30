package io.turnbase.bundle;

/** Whether a {@link Finding} fails the bundle. */
public enum Severity {
  /** Fatal: any one error fails the bundle. */
  ERROR,
  /** Advisory: never causes rejection on its own. */
  WARNING
}
