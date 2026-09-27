package io.turnbase.gdk.model;

import java.util.List;
import java.util.UUID;

/**
 * A team and its players, as configured in the lobby. Maps to proto {@code Team}. {@code name} is
 * resolved from the manifest team template.
 */
public record Team(UUID id, String name, List<Player> players) {}
