package io.turnbase.harness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.turnbase.gdk.model.Player;
import io.turnbase.gdk.model.Team;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HarnessTest {

  @TempDir Path tmp;

  private static HarnessConfig cfg(boolean strict, boolean failOnWarnings) {
    return new HarnessConfig(7777, List.of(), Map.of(), List.of(), strict, failOnWarnings);
  }

  @Test
  void validProjectLaunchesAndCarriesTheUncheckableRangeWarning() throws Exception {
    Path project = Projects.project(tmp, Projects.GOOD_STATE);
    PreLaunchCheck.Outcome out = PreLaunchCheck.run(project, cfg(true, true));
    assertThat(out.validation().errors()).isEmpty();
    assertThat(out.launch()).isTrue(); // the harness's own warning must not trip failOnWarnings
    assertThat(out.findings()).anyMatch(f -> f.code().equals(PreLaunchCheck.GDK_RANGE_UNCHECKED));
  }

  @Test
  void fatalFindingStopsTheLaunch() throws Exception {
    Path project = Projects.project(tmp, Projects.GOOD_STATE);
    Files.delete(project.resolve("frontend/main.js"));
    PreLaunchCheck.Outcome out = PreLaunchCheck.run(project, cfg(true, false));
    assertThat(out.launch()).isFalse();
    assertThat(out.findings()).anyMatch(f -> f.code().equals("bundle.paths.frontend"));
  }

  @Test
  void warningPrintsAndLaunchContinuesUnlessFailOnWarnings() throws Exception {
    Path project = Projects.project(tmp, Projects.GOOD_STATE);
    Files.writeString(project.resolve("frontend/index.html"), "<html></html>"); // E6 warning
    assertThat(PreLaunchCheck.run(project, cfg(true, false)).launch()).isTrue();
    assertThat(PreLaunchCheck.run(project, cfg(true, true)).launch()).isFalse();
    assertThat(PreLaunchCheck.run(project, cfg(true, false)).validation().warnings()).isNotEmpty();
  }

  @Test
  void harnessJsonIsNotPartOfTheValidatedBundle() throws Exception {
    Path project = Projects.project(tmp, Projects.GOOD_STATE);
    Files.writeString(
        project.resolve("harness.json"),
        "{\"port\":9000,\"strictSerialization\":false,\"parameters\":{\"rounds\":3}}");
    HarnessConfig c = HarnessConfig.load(project);
    assertThat(c.port()).isEqualTo(9000);
    assertThat(c.strictSerialization()).isFalse();
    assertThat(c.parameters()).containsKey("rounds");
    assertThat(c.parameters().values()).extracting(v -> v.asInt()).containsExactly(3);
    assertThat(HarnessConfig.load(tmp.resolve("project/backend")).port()).isEqualTo(7777);
    assertThat(PreLaunchCheck.run(project, c).validation().warnings()).isEmpty();
  }

  @Test
  void rosterIsCountMinTimesSizeMax() throws Exception {
    var templates = new ObjectMapper().readTree(Projects.MANIFEST).get("teamTemplates");
    List<Team> teams = RosterSynthesizer.synthesize(templates, cfg(true, false));
    assertThat(teams).hasSize(2);
    assertThat(teams).allSatisfy(t -> assertThat(t.players()).hasSize(2));
    assertThat(teams.get(0).players()).allMatch(p -> p.teamId().equals(teams.get(0).id()));
  }

  @Test
  void configuredPlayersOverrideTheDerivedRoster() throws Exception {
    var templates = new ObjectMapper().readTree(Projects.MANIFEST).get("teamTemplates");
    HarnessConfig c =
        new HarnessConfig(
            7777,
            List.of(
                new HarnessConfig.PlayerSpec("alice", 1), new HarnessConfig.PlayerSpec("bob", 2)),
            Map.of(),
            List.of(),
            true,
            false);
    List<Team> teams = RosterSynthesizer.synthesize(templates, c);
    assertThat(teams.get(0).players()).extracting(Player::displayName).containsExactly("alice");
    assertThat(teams.get(1).players()).extracting(Player::displayName).containsExactly("bob");
    HarnessConfig bad =
        new HarnessConfig(
            7777, List.of(new HarnessConfig.PlayerSpec("x", 9)), Map.of(), List.of(), true, false);
    assertThatThrownBy(() -> RosterSynthesizer.synthesize(templates, bad))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private io.turnbase.harness.LocalGame load(Path project, HarnessConfig c, List<String> warnings)
      throws Exception {
    // Straight to the loader: the static F7 check (rightly) rejects BAD_STATE before launch, and
    // the runtime round trip is what these tests exercise.
    var bundle =
        new io.turnbase.bundle.BundleValidator.Bundle(
            new io.turnbase.bundle.ArchiveReadResult(List.of(), List.of()),
            new ObjectMapper().readTree(Projects.MANIFEST),
            Projects.MANIFEST,
            "backend/game.jar",
            "0.3.0",
            null);
    return GameLoader.load(project, bundle, c, warnings::add);
  }

  @Test
  void roundTripPassesAfterInitAndEveryAction() throws Exception {
    Path project = Projects.project(tmp, Projects.GOOD_STATE);
    List<String> warnings = new ArrayList<>();
    LocalGame game = load(project, cfg(true, false), warnings);
    game.init();
    Player p = game.teams().get(0).players().get(0);
    for (int i = 0; i < 3; i++) {
      game.action(p, "{}");
    }
    assertThat(game.state()).isPresent();
    assertThat(game.state().get().toString()).contains("count=3");
    assertThat(warnings).isEmpty();
  }

  @Test
  void strictSerializationFailsTheRunOnMismatch() throws Exception {
    Path project = Projects.project(tmp, Projects.BAD_STATE);
    LocalGame game = load(project, cfg(true, false), new ArrayList<>());
    assertThatThrownBy(game::init)
        .isInstanceOf(SerializationMismatchException.class)
        .hasMessageContaining("onInit")
        .hasMessageContaining("before:")
        .hasMessageContaining("after:");
  }

  @Test
  void nonStrictSerializationWarnsAndContinues() throws Exception {
    Path project = Projects.project(tmp, Projects.BAD_STATE);
    List<String> warnings = new ArrayList<>();
    LocalGame game = load(project, cfg(false, false), warnings);
    game.init();
    game.action(game.teams().get(0).players().get(0), "{}");
    assertThat(warnings).hasSize(2).allMatch(w -> w.contains("did not survive"));
    assertThat(game.state()).isPresent();
  }

  @Test
  void emptyResyncViewWarns() throws Exception {
    Path project = Projects.project(tmp, Projects.GOOD_STATE);
    List<String> warnings = new ArrayList<>();
    LocalGame game = load(project, cfg(true, false), warnings);
    game.init();
    game.resync(null);
    assertThat(warnings).singleElement().asString().contains("spectator");
  }
}
