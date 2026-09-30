package io.turnbase.harness;

import com.fasterxml.jackson.databind.JsonNode;
import io.turnbase.bundle.BundleValidator;
import io.turnbase.gdk.GameState;
import io.turnbase.gdk.TurnBaseGame;
import io.turnbase.gdk.model.GameParameters;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Loads a validated bundle's backend JAR into the harness JVM and wraps the {@code
 * backend.entryPoint} instance as a {@link LocalGame}. Only ever called after {@link
 * PreLaunchCheck} passed, so the JAR has already been through the static checks — but loading it
 * does execute it, which is the whole point of the harness and the reason it must never run at
 * upload ({@code NFR-UPLOAD-002}).
 */
public final class GameLoader {

  private GameLoader() {}

  /**
   * @param projectDir the game project directory
   * @param bundle the validated bundle
   * @param config the harness configuration
   * @param warnings receives runtime warnings
   * @return the game, not yet initialised
   * @throws ReflectiveOperationException when the entry point cannot be instantiated
   */
  @SuppressWarnings("unchecked")
  public static LocalGame load(
      Path projectDir,
      BundleValidator.Bundle bundle,
      HarnessConfig config,
      Consumer<String> warnings)
      throws ReflectiveOperationException {
    JsonNode manifest = bundle.manifest();
    String jarPath = bundle.backendPath();
    URL jar;
    try {
      jar = projectDir.resolve(jarPath).toUri().toURL();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    // Parent is the harness loader: the GDK types must be the same Class objects on both sides.
    URLClassLoader loader = new URLClassLoader(new URL[] {jar}, GameLoader.class.getClassLoader());
    Class<?> type = loader.loadClass(manifest.path("backend").path("entryPoint").asText());
    TurnBaseGame<GameState> game =
        (TurnBaseGame<GameState>) type.getDeclaredConstructor().newInstance();
    return new LocalGame(
        game,
        LocalGame.resolveStateType(type),
        RosterSynthesizer.synthesize(manifest.path("teamTemplates"), config),
        parameters(manifest.path("parameters"), config.parameters()),
        config.strictSerialization(),
        warnings);
  }

  /** Manifest defaults, overridden by {@code harness.json}. */
  static GameParameters parameters(JsonNode declared, Map<String, JsonNode> overrides) {
    Map<String, JsonNode> values = new LinkedHashMap<>();
    for (JsonNode p : declared) {
      values.put(p.path("key").asText(), p.path("default"));
    }
    values.putAll(overrides);
    return new MapParameters(values);
  }

  private record MapParameters(Map<String, JsonNode> values) implements GameParameters {
    private JsonNode get(String key) {
      JsonNode v = values.get(key);
      if (v == null) {
        throw new io.turnbase.gdk.model.ParameterAccessException(key);
      }
      return v;
    }

    @Override
    public int getInt(String key) {
      return get(key).asInt();
    }

    @Override
    public double getDecimal(String key) {
      return get(key).asDouble();
    }

    @Override
    public boolean getBoolean(String key) {
      return get(key).asBoolean();
    }

    @Override
    public String getString(String key) {
      return get(key).asText();
    }

    @Override
    public String getEnum(String key) {
      return get(key).asText();
    }

    @Override
    public boolean contains(String key) {
      return values.containsKey(key);
    }

    @Override
    public Set<String> keys() {
      return values.keySet();
    }
  }
}
