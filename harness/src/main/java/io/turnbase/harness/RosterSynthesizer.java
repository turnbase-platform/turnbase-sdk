package io.turnbase.harness;

import com.fasterxml.jackson.databind.JsonNode;
import io.turnbase.gdk.model.Player;
import io.turnbase.gdk.model.Team;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Builds the roster the game sees at {@code init} ({@code test-harness.md} §4, FR-HARNESS-007): the
 * manifest's {@code teamTemplates} at the {@code count.min} × {@code size.max} shape the platform
 * uses at room creation ({@code game-rooms.md} FR-TEAM-012), unless {@code harness.json} names the
 * players.
 */
public final class RosterSynthesizer {

  private RosterSynthesizer() {}

  /**
   * @param teamTemplates the manifest's {@code teamTemplates} array
   * @param config the harness configuration
   * @return the teams, each with its players
   * @throws IllegalArgumentException when a configured player names a team that does not exist
   */
  public static List<Team> synthesize(JsonNode teamTemplates, HarnessConfig config) {
    List<String> teamNames = new ArrayList<>();
    List<Integer> sizes = new ArrayList<>();
    for (JsonNode template : teamTemplates) {
      String name = template.path("name").asText("Team");
      int count = template.path("count").path("min").asInt(1);
      int size = template.path("size").path("max").asInt(1);
      for (int i = 0; i < count; i++) {
        teamNames.add(
            count > 1 || teamTemplates.size() > 1 ? name + " " + (teamNames.size() + 1) : name);
        sizes.add(size);
      }
    }
    List<UUID> teamIds = teamNames.stream().map(n -> UUID.randomUUID()).toList();
    List<List<Player>> members = new ArrayList<>();
    teamNames.forEach(n -> members.add(new ArrayList<>()));

    if (config.players().isEmpty()) {
      for (int t = 0; t < teamNames.size(); t++) {
        for (int p = 1; p <= sizes.get(t); p++) {
          members
              .get(t)
              .add(new Player(UUID.randomUUID(), "Player " + (t + 1) + "." + p, teamIds.get(t)));
        }
      }
    } else {
      int next = 0;
      for (HarnessConfig.PlayerSpec spec : config.players()) {
        int t = spec.team() == null ? next++ % teamNames.size() : spec.team() - 1;
        if (t < 0 || t >= teamNames.size()) {
          throw new IllegalArgumentException(
              "player "
                  + spec.name()
                  + " names team "
                  + spec.team()
                  + ", but the manifest yields "
                  + teamNames.size()
                  + " team(s)");
        }
        members.get(t).add(new Player(UUID.randomUUID(), spec.name(), teamIds.get(t)));
      }
    }
    List<Team> teams = new ArrayList<>();
    for (int t = 0; t < teamNames.size(); t++) {
      teams.add(new Team(teamIds.get(t), teamNames.get(t), List.copyOf(members.get(t))));
    }
    return List.copyOf(teams);
  }
}
