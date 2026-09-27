package io.turnbase.gdk.context;

import io.turnbase.gdk.model.Player;

/** {@code action} input. */
public interface ActionContext {
  /**
   * The acting player; identity is attached server-side and cannot be spoofed by the FE (FR-FA-002,
   * NFR-SEC-001).
   */
  Player player();

  /**
   * Deserialize the opaque action payload into the developer's action type (FR-GDK-005). The GDK
   * Host performs JSON deserialization.
   *
   * @param type the action class; for a game with multiple move kinds this is typically one sealed
   *     interface (e.g. {@code Move.class})
   * @param <A> the developer's action type
   * @return the decoded action
   * @throws ActionDecodeException if the payload cannot be read as {@code type}
   */
  <A> A action(Class<A> type);
}
