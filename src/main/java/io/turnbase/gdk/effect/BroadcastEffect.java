package io.turnbase.gdk.effect;

/**
 * To all players' FEs and all spectators (FR-EFF-001).
 *
 * @param payload an opaque, developer-typed, non-null payload serialized to JSON by the GDK Host;
 *     at most 10 MB serialized (NFR-SIZE-002)
 */
public record BroadcastEffect(Object payload) implements Effect {}
