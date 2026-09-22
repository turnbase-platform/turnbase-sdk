package io.turnbase.gdk.effect;

/**
 * A side effect returned by a game handler. Sealed so the GDK Host can map effects to proto with an
 * exhaustive {@code switch}; developers never construct variants directly — they use the {@link
 * Effects} factory.
 */
public sealed interface Effect
    permits BroadcastEffect, SendToPlayerEffect, SendToTeamEffect, LogEffect, GameOverEffect {}
