# turnbase-sdk

The public Game Development Kit — `TurnBaseGame`, effect/input types, and the TypeScript
`TurnBaseClient` — published for third-party developers to build TurnBase games against.

**This is the only repository third parties compile against.** See `CLAUDE.md` for the three
consequences that follow from that and don't apply anywhere else in the project.

## What's here today (`IMP-M0-02`)

The build-and-publish floor only: `io.turnbase.gdk.GameState`, the five packages
`backend-gdk-api.md` §2 fixes (`context`, `effect`, `result`, `model` are still empty — the
layout is part of the published surface, so all five exist before their contents do), and the
declared TypeScript types from `frontend-gdk-api.md` §5/§6/§9/§10.

**No `TurnBaseGame`, no context/result/effect types, and no `TurnBaseClient` ship here.** The
Java type surface is `IMP-M3-01`; the frontend client is `IMP-M3-12`. If you expected to find
either, you're looking for one of those.

## Build

```bash
./mvnw spotless:apply              # before opening a PR, not after CI rejects it
./mvnw verify                      # enforcer -> spotless -> checkstyle -> Error Prone/NullAway -> tests -> jacoco
./mvnw install                     # to the local repository

npm ci
npm run lint && npm run typecheck && npm run build && npm test
```

Toolchain is pinned in `.sdkmanrc` (`21.0.2-graalce`) and `.nvmrc` (`22.20.0`). Enable the local
fast-fail once per clone:

```bash
git config core.hooksPath .githooks
```

## Source layout

TypeScript lives under `src/main/ts/` (tests under `src/test/ts/`), matching Maven's
`src/main/<lang>` shape rather than a conventional npm `src/`. `npm run build` and friends run
from the repository root, exactly as documented here.

## The one repository that runs Checkstyle

`conventions.md` §2: Spotless formats every repository, but Checkstyle here — and only
here — enforces one additional rule: **Javadoc on every public type.** That's the one thing a
formatter cannot check, and this artifact's Javadoc is the documentation a third-party
developer gets. It's bound to `validate`, so a missing-Javadoc build fails at the same cheap
point a formatting one does.

## The dependency this artifact must never carry

`turnbase-sdk` must have no dependency on `io.turnbase:turnbase-grpc` or
`turnbase-game-host` — not even transitively. `IMP-M3-10` (the first official game) fails if
the GDK contract needs one, because that would mean the contract was never self-sufficient.
The enforcer's `bannedDependencies` rule fails the build if either appears, at any scope,
so this doesn't rely on anyone remembering to check it by hand.

## Two coverage gates are currently inert, and deliberately so

Both JaCoCo (Java, 70% line, merged unit+integration) and Vitest's coverage thresholds (TS,
70%) measure a types-only surface today and report 100% over zero executable lines — the same
shape `turnbase-common`'s pom documents. Neither becomes real until `IMP-M3-01` and
`IMP-M3-12` land actual logic. Do not "fix" either by lowering the threshold or by counting
something else to make the number look real.

## Consuming this artifact

Maven consumers declare `io.turnbase:turnbase-sdk` at `provided` scope (Maven) or
`compileOnly` (Gradle) — never bundled in the game JAR (FR-GDK-014). npm consumers declare
`@turnbase/gdk` as a normal dependency; it ships zero runtime dependencies of its own
(FR-FEGDK-008).

**Pre-1.0, breaking changes are expected.** NFR-COMPAT-001's backward-compatibility guarantee
begins at the `v1.0.0` release; the committed version here (`0.1.0-SNAPSHOT` / `0.1.0`) may
still change shape between iterations.

## If a specification is wrong

**Stop, and fix the document in the parent repository first.** A defect published in this
artifact reaches people outside the project and cannot be quietly corrected.

## Third-party licences

What this repository distributes is audited for its dependencies' licences (allowed families:
`docs/implementation/conventions.md` §6 *Third-party licences*). The report is generated, not committed:

```bash
./mvnw org.codehaus.mojo:license-maven-plugin:2.8.0:aggregate-add-third-party -Dlicense.excludedScopes=test,provided
# -> target/generated-sources/license/THIRD-PARTY.txt
```

The npm half: `npm run licenses` (checker pinned in the script; `licenses.csv`, git-ignored).
