package io.turnbase.gdk.context;

import io.turnbase.gdk.model.GameParameters;
import io.turnbase.gdk.model.Team;
import java.util.List;

/** {@code init} input. */
public interface InitContext {
  /** Teams with their assigned players, as configured in the lobby. */
  List<Team> teams();

  /** Host-configured game parameters (FR-GDK-004). */
  GameParameters parameters();

  /**
   * A deterministic random seed for this session.
   *
   * <p>{@code backend-gdk-api.md} §9 forbids "randomness without a seed carried in state". This is
   * that seed. Use it to construct {@code new Random(ctx.seed())} and carry the resulting values —
   * or the seed itself — in game state; never call {@code new Random()}, {@code Math.random()}, or
   * any clock.
   *
   * <p>Derived by the GDK Host from the session id, so it is stable for the life of a session and
   * across any replay of {@code CreateSession}, and different for every rematch (a rematch is a new
   * session with a new id). Two sessions never share a seed; one session never changes it.
   */
  long seed();
}
