package io.turnbase.gdk.result;

/**
 * The view one viewer sees right now. No state, no effects — a resync cannot change the game
 * (FR-IN-006, FR-RESP-005).
 *
 * @param view an opaque, developer-typed, non-null payload serialized to JSON by the GDK Host, like
 *     any effect payload; at most 10 MB serialized (NFR-SIZE-002)
 */
public record ViewResult(Object view) {

  /**
   * @param view the payload to show this viewer
   * @return a {@link ViewResult} wrapping {@code view}
   */
  public static ViewResult of(Object view) {
    return new ViewResult(view);
  }

  /**
   * @return a {@link ViewResult} for a game with nothing to show this viewer yet
   */
  public static ViewResult empty() {
    return new ViewResult(new Object());
  }
}
