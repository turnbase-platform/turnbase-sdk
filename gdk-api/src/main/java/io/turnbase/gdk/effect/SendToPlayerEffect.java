package io.turnbase.gdk.effect;

import java.util.UUID;

/**
 * To one player's FE only (FR-EFF-002). Not delivered to spectators or other players.
 *
 * @param playerId the recipient player's id (UUID)
 * @param payload an opaque, developer-typed, non-null payload serialized to JSON by the GDK Host;
 *     at most 10 MB serialized (NFR-SIZE-002)
 */
public record SendToPlayerEffect(UUID playerId, Object payload) implements Effect {}
