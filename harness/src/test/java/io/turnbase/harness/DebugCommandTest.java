package io.turnbase.harness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.turnbase.gdk.context.PlayerLeftReason;
import java.util.List;
import org.junit.jupiter.api.Test;

class DebugCommandTest {

  @Test
  void flagsBuildOneCommandPerInputForTheNamedPlayer() {
    List<DebugCommand> cmds =
        DebugCommand.fromArgs(
            List.of(
                "--player",
                "bob",
                "--action",
                "{\"card\":\"7H\"}",
                "--player",
                "amy",
                "--leave",
                "grace_expired",
                "--resync",
                "--join"));
    assertThat(cmds)
        .containsExactly(
            new DebugCommand(DebugCommand.Kind.ACTION, "bob", "{\"card\":\"7H\"}", null),
            new DebugCommand(
                DebugCommand.Kind.LEAVE, "amy", null, PlayerLeftReason.GRACE_PERIOD_EXPIRED),
            new DebugCommand(DebugCommand.Kind.RESYNC, "amy", null, null),
            new DebugCommand(DebugCommand.Kind.JOIN, "amy", null, null));
  }

  @Test
  void leaveWithoutAReasonMeansLeft() {
    assertThat(DebugCommand.fromArgs(List.of("--player", "a", "--leave")).get(0).reason())
        .isEqualTo(PlayerLeftReason.LEFT);
  }

  @Test
  void jsonAndFlagsAgree() throws Exception {
    var json = new ObjectMapper().readTree("{\"player\":\"bob\",\"leave\":\"kicked\"}");
    assertThat(DebugCommand.fromJson(json))
        .isEqualTo(DebugCommand.fromArgs(List.of("--player", "bob", "--leave", "kicked")).get(0));
  }

  @Test
  void malformedInputIsRefused() throws Exception {
    assertThatThrownBy(() -> DebugCommand.fromArgs(List.of("--action", "{}")))
        .hasMessageContaining("--player must come before");
    assertThatThrownBy(() -> DebugCommand.fromArgs(List.of("--bogus")))
        .hasMessageContaining("unknown flag");
    assertThatThrownBy(() -> DebugCommand.fromArgs(List.of("--player")))
        .hasMessageContaining("needs a value");
    var both = new ObjectMapper().readTree("{\"player\":\"a\",\"join\":true,\"resync\":true}");
    assertThatThrownBy(() -> DebugCommand.fromJson(both)).hasMessageContaining("exactly one");
  }
}
