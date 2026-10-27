package io.turnbase.gdk.effect;

import io.turnbase.gdk.model.GameOutcome;
import java.util.UUID;

/**
 * Factory for {@link Effect} variants (FR-GDK-008). Developers never construct a variant record
 * directly.
 *
 * <p>Payloads are opaque, developer-typed objects serialized to JSON by the GDK Host (FR-STATE-002,
 * CON-004); the platform never inspects them. {@code payload} and {@code resultsPayload} must be
 * non-null (use an empty object for "no data"). A single serialized effect payload must not exceed
 * 10 MB (NFR-SIZE-002).
 */
public final class Effects {
  private Effects() {}

  /**
   * @param payload an opaque, developer-typed, non-null payload
   * @return a {@link BroadcastEffect} to all players' FEs and all spectators (FR-EFF-001)
   */
  public static Effect broadcast(Object payload) {
    return new BroadcastEffect(payload);
  }

  /**
   * @param playerId the recipient player's id (UUID)
   * @param payload an opaque, developer-typed, non-null payload
   * @return a {@link SendToPlayerEffect} to one player's FE only (FR-EFF-002)
   */
  public static Effect sendToPlayer(UUID playerId, Object payload) {
    return new SendToPlayerEffect(playerId, payload);
  }

  /**
   * @param teamId the recipient team's id (UUID)
   * @param payload an opaque, developer-typed, non-null payload
   * @return a {@link SendToTeamEffect} to all players on one team (FR-EFF-003)
   */
  public static Effect sendToTeam(UUID teamId, Object payload) {
    return new SendToTeamEffect(teamId, payload);
  }

  /**
   * @param text the log entry text, supporting basic Markdown (FR-LOG-002)
   * @return a {@link LogEffect} (FR-EFF-004)
   */
  public static LogEffect log(String text) {
    return new LogEffect(text);
  }

  /**
   * @param outcome the structured outcome for platform use
   * @param resultsPayload an opaque, developer-typed, non-null payload for the game FE
   * @return a {@link GameOverEffect} ending the session (FR-EFF-005/006/007)
   */
  public static Effect gameOver(GameOutcome outcome, Object resultsPayload) {
    return new GameOverEffect(outcome, resultsPayload);
  }
}
