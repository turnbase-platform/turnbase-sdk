package io.turnbase.gdk.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Proves {@link GameOutcome.Builder} accumulates team/player results and the draw flag. */
class GameOutcomeTest {

  @Test
  void builderAccumulatesTeamAndPlayerResultsInCallOrder() {
    UUID team1 = UUID.randomUUID();
    UUID team2 = UUID.randomUUID();
    UUID player1 = UUID.randomUUID();

    GameOutcome outcome =
        GameOutcome.builder()
            .teamResult(team1, 1, Map.of("score", "10"))
            .teamResult(team2, 2, Map.of())
            .playerResult(player1, Map.of("mvp", "true"))
            .draw(false)
            .build();

    assertThat(outcome.draw()).isFalse();
    assertThat(outcome.teamResults())
        .containsExactly(
            new GameOutcome.TeamResult(team1, 1, Map.of("score", "10")),
            new GameOutcome.TeamResult(team2, 2, Map.of()));
    assertThat(outcome.playerResults())
        .containsExactly(new GameOutcome.PlayerResult(player1, Map.of("mvp", "true")));
  }

  @Test
  void aDrawMayShareAPlacementAcrossTeams() {
    UUID team1 = UUID.randomUUID();
    UUID team2 = UUID.randomUUID();

    GameOutcome outcome =
        GameOutcome.builder()
            .teamResult(team1, 1, Map.of())
            .teamResult(team2, 1, Map.of())
            .draw(true)
            .build();

    assertThat(outcome.draw()).isTrue();
    assertThat(outcome.teamResults())
        .extracting(GameOutcome.TeamResult::placement)
        .containsExactly(1, 1);
  }

  @Test
  void anEmptyBuilderProducesEmptyResultsAndNoDraw() {
    GameOutcome outcome = GameOutcome.builder().build();

    assertThat(outcome.teamResults()).isEmpty();
    assertThat(outcome.playerResults()).isEmpty();
    assertThat(outcome.draw()).isFalse();
  }

  @Test
  void builtResultListsAreImmutable() {
    GameOutcome outcome = GameOutcome.builder().build();

    assertThatThrownBy(
            () ->
                outcome
                    .teamResults()
                    .add(new GameOutcome.TeamResult(UUID.randomUUID(), 1, Map.of())))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
