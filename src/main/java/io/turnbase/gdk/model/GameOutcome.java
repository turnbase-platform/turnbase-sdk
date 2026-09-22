package io.turnbase.gdk.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Consumed by the platform for game history and statistics; <b>not</b> delivered to the FE (the FE
 * gets the separate {@code resultsPayload} of {@code GameOverEffect}) (FR-GDK-010, FR-OUTCOME-*).
 */
public record GameOutcome(
    List<TeamResult> teamResults, List<PlayerResult> playerResults, boolean draw) {

  /**
   * @param teamId the team's id (UUID)
   * @param placement the 1-based finishing position (FR-OUTCOME-001); when {@link
   *     GameOutcome#draw()} is true, multiple teams may share a placement (FR-OUTCOME-003)
   * @param stats an opaque {@code string→string} stats map, stored but never interpreted by the
   *     platform (FR-OUTCOME-004); {@code Map.of()} for none
   */
  public record TeamResult(UUID teamId, int placement, Map<String, String> stats) {}

  /**
   * @param playerId the player's id (UUID)
   * @param stats an opaque {@code string→string} stats map, stored but never interpreted by the
   *     platform (FR-OUTCOME-004); {@code Map.of()} for none
   */
  public record PlayerResult(UUID playerId, Map<String, String> stats) {}

  /**
   * @return a new, empty {@link Builder}
   */
  public static Builder builder() {
    return new Builder();
  }

  /** Accumulates team/player results and the draw flag for a {@link GameOutcome}. */
  public static final class Builder {
    private final List<TeamResult> teamResults = new ArrayList<>();
    private final List<PlayerResult> playerResults = new ArrayList<>();
    private boolean draw;

    private Builder() {}

    /**
     * @param teamId the team's id (UUID)
     * @param placement the 1-based finishing position (FR-OUTCOME-001)
     * @param stats an opaque {@code string→string} stats map ({@code Map.of()} for none)
     * @return this builder
     */
    public Builder teamResult(UUID teamId, int placement, Map<String, String> stats) {
      teamResults.add(new TeamResult(teamId, placement, stats));
      return this;
    }

    /**
     * @param playerId the player's id (UUID)
     * @param stats an opaque {@code string→string} stats map ({@code Map.of()} for none)
     * @return this builder
     */
    public Builder playerResult(UUID playerId, Map<String, String> stats) {
      playerResults.add(new PlayerResult(playerId, stats));
      return this;
    }

    /**
     * @param draw whether the game ended in a draw (FR-OUTCOME-003)
     * @return this builder
     */
    public Builder draw(boolean draw) {
      this.draw = draw;
      return this;
    }

    /**
     * @return the built {@link GameOutcome}
     */
    public GameOutcome build() {
      return new GameOutcome(List.copyOf(teamResults), List.copyOf(playerResults), draw);
    }
  }
}
