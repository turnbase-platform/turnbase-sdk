package io.turnbase.gdk;

/**
 * Marker for game-state types. Carries no methods; signals to the GDK Host that the implementing
 * type is the session's serializable state (FR-GDK-012).
 *
 * <p>State types are plain JSON-serializable Java objects — records or POJOs with serializable
 * fields (FR-GDK-013, CON-004). No serialization code is written by the developer; the GDK Host
 * serializes and deserializes state transparently, subject to the rules in {@code
 * backend-gdk-api.md} §5.1 (a record or a class compiled with {@code -parameters},
 * string/enum/UUID/boxed-integral map keys only, no interface- or {@code Object}-typed fields, no
 * reference cycles).
 */
public interface GameState {}
