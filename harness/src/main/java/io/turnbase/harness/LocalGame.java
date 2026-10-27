package io.turnbase.harness;

import io.turnbase.gdk.GameState;
import io.turnbase.gdk.TurnBaseGame;
import io.turnbase.gdk.context.ActionContext;
import io.turnbase.gdk.context.InitContext;
import io.turnbase.gdk.context.PlayerJoinedContext;
import io.turnbase.gdk.context.PlayerLeftContext;
import io.turnbase.gdk.context.PlayerLeftReason;
import io.turnbase.gdk.context.ResyncContext;
import io.turnbase.gdk.context.ViewerRole;
import io.turnbase.gdk.effect.Effect;
import io.turnbase.gdk.model.GameParameters;
import io.turnbase.gdk.model.Player;
import io.turnbase.gdk.model.Team;
import io.turnbase.gdk.result.ActionResult;
import io.turnbase.gdk.result.InitResult;
import io.turnbase.gdk.result.PlayerJoinedResult;
import io.turnbase.gdk.result.PlayerLeftResult;
import io.turnbase.gdk.result.ViewResult;
import io.turnbase.serialization.GdkSerialization;
import io.turnbase.serialization.SerializationLimits;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * The game backend as an ordinary object in the harness's own JVM ({@code test-harness.md} §2): no
 * Espresso, no sandbox, state held in a field. After {@code onInit} and after <em>every</em>
 * handler that returns state, the state goes through the executing serialization round trip ({@code
 * §6}, FR-HARNESS-010) using the GDK Host's {@code ObjectMapper} configuration, from {@link
 * GdkSerialization}.
 */
public final class LocalGame {

  private final TurnBaseGame<GameState> game;
  private final Class<? extends GameState> stateType;
  private final GdkSerialization serialization = new GdkSerialization();
  private final boolean strict;
  private final Consumer<String> warnings;
  private final List<Team> teams;
  private final GameParameters parameters;
  private @Nullable GameState state;

  /**
   * @param game the instantiated {@code backend.entryPoint}
   * @param stateType the resolved type argument {@code S} of {@code TurnBaseGame<S>}
   * @param teams the synthesized roster
   * @param parameters the resolved game parameters
   * @param strict {@link HarnessConfig#strictSerialization()}
   * @param warnings receives round-trip mismatches when not strict, and empty-view warnings
   */
  LocalGame(
      TurnBaseGame<GameState> game,
      Class<? extends GameState> stateType,
      List<Team> teams,
      GameParameters parameters,
      boolean strict,
      Consumer<String> warnings) {
    this.game = game;
    this.stateType = stateType;
    this.teams = teams;
    this.parameters = parameters;
    this.strict = strict;
    this.warnings = warnings;
  }

  /**
   * Resolves {@code S} from the game class's {@code TurnBaseGame<S>} supertype.
   *
   * @param gameClass the entry point class
   * @return the state type
   * @throws IllegalArgumentException when it cannot be resolved
   */
  @SuppressWarnings("unchecked")
  static Class<? extends GameState> resolveStateType(Class<?> gameClass) {
    for (Class<?> c = gameClass; c != null; c = c.getSuperclass()) {
      for (Type t : c.getGenericInterfaces()) {
        if (t instanceof ParameterizedType p && p.getRawType() == TurnBaseGame.class) {
          if (p.getActualTypeArguments()[0] instanceof Class<?> s) {
            return (Class<? extends GameState>) s;
          }
        }
      }
    }
    throw new IllegalArgumentException(
        "cannot resolve the state type of " + gameClass.getName() + " from TurnBaseGame<S>");
  }

  /**
   * Runs {@code onInit}.
   *
   * @return the effects it returned
   */
  public List<Effect> init() {
    InitResult<GameState> result =
        game.onInit(
            new InitContext() {
              @Override
              public List<Team> teams() {
                return teams;
              }

              @Override
              public GameParameters parameters() {
                return parameters;
              }

              @Override
              public long seed() {
                return System.nanoTime();
              }
            });
    return accept(result.state(), result.effects(), "onInit");
  }

  /**
   * Runs {@code onAction}.
   *
   * @param player the acting player
   * @param actionJson the action payload
   * @return the effects it returned
   */
  public List<Effect> action(Player player, String actionJson) {
    ActionResult<GameState> result =
        game.onAction(
            current(),
            new ActionContext() {
              @Override
              public Player player() {
                return player;
              }

              @Override
              public <A> A action(Class<A> type) {
                return serialization.readAction(actionJson, type);
              }
            });
    return accept(result.state(), result.effects(), "onAction");
  }

  /**
   * Runs {@code onPlayerJoined}.
   *
   * @param player the joining player
   * @return the effects it returned
   */
  public List<Effect> playerJoined(Player player) {
    PlayerJoinedResult<GameState> result =
        game.onPlayerJoined(current(), (PlayerJoinedContext) () -> player);
    return accept(result.state(), result.effects(), "onPlayerJoined");
  }

  /**
   * Runs {@code onPlayerLeft}.
   *
   * @param player the departing player
   * @param reason why
   * @return the effects it returned
   */
  public List<Effect> playerLeft(Player player, PlayerLeftReason reason) {
    PlayerLeftResult<GameState> result =
        game.onPlayerLeft(
            current(),
            new PlayerLeftContext() {
              @Override
              public Player player() {
                return player;
              }

              @Override
              public PlayerLeftReason reason() {
                return reason;
              }
            });
    return accept(result.state(), result.effects(), "onPlayerLeft");
  }

  /**
   * Runs {@code onResync} for one viewer, warning when it returns an empty view ({@code
   * websocket.md} OQ-002).
   *
   * @param viewer the viewing player, or {@code null} for a spectator
   * @return the view
   */
  public ViewResult resync(@Nullable Player viewer) {
    ViewResult view =
        game.onResync(
            current(),
            new ResyncContext() {
              @Override
              public ViewerRole role() {
                return viewer == null ? ViewerRole.SPECTATOR : ViewerRole.PLAYER;
              }

              @Override
              public Optional<Player> viewer() {
                return Optional.ofNullable(viewer);
              }
            });
    if (isEmpty(view.view())) {
      warnings.accept(
          "onResync returned an empty view for "
              + (viewer == null ? "the spectator" : viewer.displayName())
              + "; that viewer sees a blank screen until the next broadcast.");
    }
    return view;
  }

  private static boolean isEmpty(Object view) {
    return view.getClass() == Object.class
        || (view instanceof java.util.Map<?, ?> m && m.isEmpty());
  }

  private GameState current() {
    GameState s = state;
    if (s == null) {
      throw new IllegalStateException("init() has not run");
    }
    return s;
  }

  private List<Effect> accept(GameState next, List<? extends Effect> effects, String handler) {
    roundTrip(next, handler);
    state = next;
    return new ArrayList<>(effects);
  }

  private void roundTrip(GameState next, String handler) {
    String problem;
    try {
      String json = serialization.writeState(next, SerializationLimits.DEFAULT_MAX_STATE_BYTES);
      GameState back = serialization.readState(json, stateType);
      if (next.equals(back)) {
        return;
      }
      problem =
          "state after "
              + handler
              + " did not survive the round trip.\n  before: "
              + next
              + "\n  json:   "
              + json
              + "\n  after:  "
              + back;
    } catch (RuntimeException e) {
      problem = "state after " + handler + " could not be round-tripped: " + e.getMessage();
    }
    if (strict) {
      throw new SerializationMismatchException(problem);
    }
    warnings.accept(problem);
  }

  /**
   * @return the current state, for the debug panel
   */
  public Optional<GameState> state() {
    return Optional.ofNullable(state);
  }

  /**
   * @return the roster the game was initialised with
   */
  public List<Team> teams() {
    return teams;
  }
}
