package io.turnbase.gdk;

import static org.assertj.core.api.Assertions.assertThat;

import io.turnbase.gdk.context.ActionContext;
import io.turnbase.gdk.context.InitContext;
import io.turnbase.gdk.context.PlayerLeftContext;
import io.turnbase.gdk.context.PlayerLeftReason;
import io.turnbase.gdk.context.ResyncContext;
import io.turnbase.gdk.context.ViewerRole;
import io.turnbase.gdk.effect.GameOverEffect;
import io.turnbase.gdk.effect.LogEffect;
import io.turnbase.gdk.effect.SendToPlayerEffect;
import io.turnbase.gdk.model.GameParameters;
import io.turnbase.gdk.model.Player;
import io.turnbase.gdk.model.Team;
import io.turnbase.gdk.result.ActionResult;
import io.turnbase.gdk.result.InitResult;
import io.turnbase.gdk.result.PlayerLeftResult;
import io.turnbase.gdk.result.ViewResult;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Exercises the full GDK surface through {@code backend-gdk-api.md} §11's worked example, proving
 * every hook, context and result type described in §§3–8 compiles against the stated signature and
 * behaves as specified — not merely that it compiles.
 */
class GuessingGameExampleTest {

  /** {@code backend-gdk-api.md} §11, verbatim. */
  record GuessState(int secret, int attempts) implements GameState {}

  /** action payload. */
  record Guess(int value) {}

  /** FE event payload. */
  record GuessFeedback(String hint, boolean win) {}

  /** {@code backend-gdk-api.md} §11, verbatim. */
  static final class GuessingGame implements TurnBaseGame<GuessState> {

    @Override
    public InitResult<GuessState> onInit(InitContext ctx) {
      int max = ctx.parameters().getInt("maxValue");
      var state = new GuessState(new java.util.Random(ctx.seed()).nextInt(max) + 1, 0);
      return InitResult.from(state)
          .effect(
              io.turnbase.gdk.effect.Effects.log("Game started — guess a number from 1 to " + max))
          .build();
    }

    @Override
    public ActionResult<GuessState> onAction(GuessState s, ActionContext ctx) {
      int guess = ctx.action(Guess.class).value();
      var next = new GuessState(s.secret(), s.attempts() + 1);
      if (guess == s.secret()) {
        var outcome =
            io.turnbase.gdk.model.GameOutcome.builder()
                .playerResult(
                    ctx.player().id(), Map.of("attempts", String.valueOf(next.attempts())))
                .draw(false)
                .build();
        return ActionResult.from(next)
            .effect(
                io.turnbase.gdk.effect.Effects.log(
                    "**" + ctx.player().displayName() + "** guessed it!"))
            .effect(
                io.turnbase.gdk.effect.Effects.gameOver(
                    outcome, new GuessFeedback("correct", true)))
            .build();
      }
      String hint = guess < s.secret() ? "higher" : "lower";
      return ActionResult.from(next)
          .effect(
              io.turnbase.gdk.effect.Effects.sendToPlayer(
                  ctx.player().id(), new GuessFeedback(hint, false)))
          .build();
    }

    @Override
    public PlayerLeftResult<GuessState> onPlayerLeft(GuessState s, PlayerLeftContext ctx) {
      return PlayerLeftResult.of(s);
    }

    @Override
    public ViewResult onResync(GuessState s, ResyncContext ctx) {
      return ViewResult.of(new GuessFeedback("attempts so far: " + s.attempts(), false));
    }
  }

  private static final class MapGameParameters implements GameParameters {
    private final Map<String, Object> values;

    MapGameParameters(Map<String, Object> values) {
      this.values = values;
    }

    @Override
    public int getInt(String key) {
      return (int) require(key);
    }

    @Override
    public double getDecimal(String key) {
      return (double) require(key);
    }

    @Override
    public boolean getBoolean(String key) {
      return (boolean) require(key);
    }

    @Override
    public String getString(String key) {
      return (String) require(key);
    }

    @Override
    public String getEnum(String key) {
      return (String) require(key);
    }

    @Override
    public boolean contains(String key) {
      return values.containsKey(key);
    }

    @Override
    public Set<String> keys() {
      return values.keySet();
    }

    private Object require(String key) {
      if (!values.containsKey(key)) {
        throw new io.turnbase.gdk.model.ParameterAccessException("unknown key: " + key);
      }
      return values.get(key);
    }
  }

  private record FixedInitContext(List<Team> teams, GameParameters parameters, long seed)
      implements InitContext {}

  private record FixedActionContext(Player player, Object action) implements ActionContext {
    @Override
    public <A> A action(Class<A> type) {
      return type.cast(action);
    }
  }

  private record FixedResyncContext(ViewerRole role, Optional<Player> viewer)
      implements ResyncContext {}

  private static Player player(String name) {
    return new Player(UUID.randomUUID(), name, UUID.randomUUID());
  }

  @Test
  void onInitIsDeterministicForAGivenSeed() {
    var game = new GuessingGame();
    var ctx = new FixedInitContext(List.of(), new MapGameParameters(Map.of("maxValue", 100)), 42L);

    InitResult<GuessState> first = game.onInit(ctx);
    InitResult<GuessState> second = game.onInit(ctx);

    assertThat(first.state()).isEqualTo(second.state());
    assertThat(first.state().attempts()).isEqualTo(0);
    assertThat(first.state().secret()).isBetween(1, 100);
    assertThat(first.effects()).hasSize(1).first().isInstanceOf(LogEffect.class);
  }

  @Test
  void aWrongGuessSendsAHintToThatPlayerOnly() {
    var game = new GuessingGame();
    var state = new GuessState(50, 0);
    Player alice = player("Alice");
    var ctx = new FixedActionContext(alice, new Guess(10));

    ActionResult<GuessState> result = game.onAction(state, ctx);

    assertThat(result.state()).isEqualTo(new GuessState(50, 1));
    assertThat(result.effects()).hasSize(1);
    var sendToPlayer = (SendToPlayerEffect) result.effects().get(0);
    assertThat(sendToPlayer.playerId()).isEqualTo(alice.id());
    assertThat(sendToPlayer.payload()).isEqualTo(new GuessFeedback("higher", false));
  }

  @Test
  void aCorrectGuessEndsTheGameWithAStructuredOutcomeAndTheFeChannelSeparately() {
    var game = new GuessingGame();
    var state = new GuessState(50, 4);
    Player alice = player("Alice");
    var ctx = new FixedActionContext(alice, new Guess(50));

    ActionResult<GuessState> result = game.onAction(state, ctx);

    assertThat(result.state()).isEqualTo(new GuessState(50, 5));
    assertThat(result.effects()).hasSize(2);
    assertThat(result.effects().get(0)).isInstanceOf(LogEffect.class);
    var gameOver = (GameOverEffect) result.effects().get(1);
    assertThat(gameOver.outcome().draw()).isFalse();
    assertThat(gameOver.outcome().playerResults())
        .containsExactly(
            new io.turnbase.gdk.model.GameOutcome.PlayerResult(
                alice.id(), Map.of("attempts", "5")));
    assertThat(gameOver.resultsPayload()).isEqualTo(new GuessFeedback("correct", true));
  }

  @Test
  void onPlayerLeftIsANoOpForASinglePlayerGame() {
    var game = new GuessingGame();
    var state = new GuessState(7, 2);
    Player alice = player("Alice");
    var ctx =
        new PlayerLeftContext() {
          @Override
          public Player player() {
            return alice;
          }

          @Override
          public PlayerLeftReason reason() {
            return PlayerLeftReason.LEFT;
          }
        };

    PlayerLeftResult<GuessState> result = game.onPlayerLeft(state, ctx);

    assertThat(result.state()).isEqualTo(state);
    assertThat(result.effects()).isEmpty();
  }

  @Test
  void onResyncNeverExposesTheSecretAndCarriesNoStateOrEffects() {
    var game = new GuessingGame();
    var state = new GuessState(50, 3);
    var ctx = new FixedResyncContext(ViewerRole.PLAYER, Optional.of(player("Alice")));

    ViewResult result = game.onResync(state, ctx);

    assertThat(result.view()).isEqualTo(new GuessFeedback("attempts so far: 3", false));
    assertThat(result.view().toString()).doesNotContain("50");
  }
}
