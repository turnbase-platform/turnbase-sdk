package io.turnbase.gdk.model;

import java.util.UUID;

/** A player as seen by game code. Maps to proto {@code PlayerRef}. */
public record Player(UUID id, String displayName, UUID teamId) {}
