# turnbase-sdk — code map

The public contract third parties compile against, plus the tools that enforce it. Four Maven modules
and one npm package; none depends on `turnbase-common` or `turnbase-game-host`. Specified in
`docs/specs/gdk-api/`; checked by `python3 scripts/check-corpus.py code-maps` from the umbrella.

## Modules

| Module | Responsibility |
|---|---|
| `gdk-api/` | Java interfaces, records and enums only: `TurnBaseGame` and its `io.turnbase.gdk.context`, `io.turnbase.gdk.effect`, `io.turnbase.gdk.result`, `io.turnbase.gdk.model` types |
| `serialization/` | `GdkSerialization` and `GdkObjectMapper`: the JSON rules shared by host and harness |
| `bundle-validator/` | `BundleValidator`: manifest and backend-JAR checks, used by the platform at upload and by the harness before launch |
| `harness/` | Local test harness: `HarnessMain`, `HarnessServer`, `MockOrchestration`, `PreLaunchCheck` |
| `src/main/ts/` | npm `@turnbase-platform/gdk`: `TurnBaseClient` and wire types |

## Flows

### Sign-in, room create/join, Game Host failure

Not involved: the SDK is a contract and carries no platform behaviour.

### Game start and a move

1. The host calls `TurnBaseGame.onInit` with an `InitContext`; the game returns an `InitResult`.
2. A move reaches `TurnBaseGame.onAction` with an `ActionContext` (`ActionDecodeException` on a bad payload) and returns an `ActionResult` holding `Effects` (`BroadcastEffect`, `SendToPlayerEffect`, `SendToTeamEffect`, `GameOverEffect`, `LogEffect`).
3. `ViewResult` answers resync via `ResyncContext`. `GdkSerialization` encodes state and payloads on the host side.
4. In the browser the game frame talks through `TurnBaseClient` (`client.ts`) by postMessage.
5. Locally the same calls are driven by `HarnessServer` with `MockOrchestration`.

### Game upload

`BundleValidator` runs `ManifestValidator`, `BackendJarAnalyzer` and `StateSerializabilityAnalyzer` over the archive read by `ArchiveReader`, returning `Finding`s the platform's upload package turns into check errors.
