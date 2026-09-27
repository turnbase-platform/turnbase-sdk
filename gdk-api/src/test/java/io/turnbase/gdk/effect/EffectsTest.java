package io.turnbase.gdk.effect;

import static org.assertj.core.api.Assertions.assertThat;

import io.turnbase.gdk.model.GameOutcome;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Proves the {@link Effects} factory produces the correct sealed {@link Effect} variant. */
class EffectsTest {

  @Test
  void broadcastCarriesThePayload() {
    Object payload = new Object();
    Effect effect = Effects.broadcast(payload);
    assertThat(effect).isInstanceOf(BroadcastEffect.class);
    assertThat(((BroadcastEffect) effect).payload()).isSameAs(payload);
  }

  @Test
  void sendToPlayerCarriesThePlayerIdAndPayload() {
    UUID playerId = UUID.randomUUID();
    Object payload = new Object();
    Effect effect = Effects.sendToPlayer(playerId, payload);
    assertThat(effect).isInstanceOf(SendToPlayerEffect.class);
    var sendToPlayer = (SendToPlayerEffect) effect;
    assertThat(sendToPlayer.playerId()).isEqualTo(playerId);
    assertThat(sendToPlayer.payload()).isSameAs(payload);
  }

  @Test
  void sendToTeamCarriesTheTeamIdAndPayload() {
    UUID teamId = UUID.randomUUID();
    Object payload = new Object();
    Effect effect = Effects.sendToTeam(teamId, payload);
    assertThat(effect).isInstanceOf(SendToTeamEffect.class);
    var sendToTeam = (SendToTeamEffect) effect;
    assertThat(sendToTeam.teamId()).isEqualTo(teamId);
    assertThat(sendToTeam.payload()).isSameAs(payload);
  }

  @Test
  void logCarriesTheText() {
    Effect effect = Effects.log("**bold** move");
    assertThat(effect).isInstanceOf(LogEffect.class);
    assertThat(((LogEffect) effect).text()).isEqualTo("**bold** move");
  }

  @Test
  void gameOverCarriesTheOutcomeAndResultsPayload() {
    GameOutcome outcome =
        GameOutcome.builder()
            .playerResult(UUID.randomUUID(), Map.of("attempts", "3"))
            .draw(false)
            .build();
    Object resultsPayload = new Object();
    Effect effect = Effects.gameOver(outcome, resultsPayload);
    assertThat(effect).isInstanceOf(GameOverEffect.class);
    var gameOver = (GameOverEffect) effect;
    assertThat(gameOver.outcome()).isSameAs(outcome);
    assertThat(gameOver.resultsPayload()).isSameAs(resultsPayload);
  }

  /** The sealed hierarchy supports an exhaustive switch with no default branch. */
  @Test
  void everyVariantIsReachableThroughAnExhaustiveSwitch() {
    List<Effect> effects =
        List.of(
            Effects.broadcast("b"),
            Effects.sendToPlayer(UUID.randomUUID(), "p"),
            Effects.sendToTeam(UUID.randomUUID(), "t"),
            Effects.log("l"),
            Effects.gameOver(GameOutcome.builder().build(), "r"));

    List<String> kinds =
        effects.stream()
            .map(
                effect ->
                    switch (effect) {
                      case BroadcastEffect e -> "broadcast";
                      case SendToPlayerEffect e -> "sendToPlayer";
                      case SendToTeamEffect e -> "sendToTeam";
                      case LogEffect e -> "log";
                      case GameOverEffect e -> "gameOver";
                    })
            .toList();

    assertThat(kinds).containsExactly("broadcast", "sendToPlayer", "sendToTeam", "log", "gameOver");
  }
}
