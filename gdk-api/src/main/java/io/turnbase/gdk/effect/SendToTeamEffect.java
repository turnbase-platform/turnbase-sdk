package io.turnbase.gdk.effect;

import java.util.UUID;

/**
 * To all players on one team (FR-EFF-003). Not delivered to spectators or players on other teams.
 *
 * @param teamId the recipient team's id (UUID)
 * @param payload an opaque, developer-typed, non-null payload serialized to JSON by the GDK Host;
 *     at most 10 MB serialized (NFR-SIZE-002)
 */
public record SendToTeamEffect(UUID teamId, Object payload) implements Effect {}
