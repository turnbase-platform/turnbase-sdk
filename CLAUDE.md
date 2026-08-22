# turnbase-sdk — CLAUDE.md

The public Game Development Kit. **The only repository in this project that third parties compile
against.** Publishes `io.turnbase:turnbase-sdk` (Maven) and an npm package.

**This file is additive** — corpus, registry and conventions live in the parent repository:

| | |
|---|---|
| Registry | `../docs/implementation/registry.md` — `IMP-M0-02`, `IMP-M3-01`, `IMP-M3-11` |
| Conventions | `../docs/implementation/conventions.md` — **normative** |
| Design | `../docs/specs/gdk-api/` — `backend-gdk-api.md`, `frontend-gdk-api.md`, `test-harness.md` |

## What makes this repo different from the other four

**It is a public contract.** Everything else here can be refactored freely; this cannot. Three
consequences that do not apply anywhere else:

1. **This artifact must have no dependency on `turnbase-common` or `turnbase-game-host`** — not even
   transitively. `IMP-M3-10` explicitly fails if the first official game needs one, because that
   would mean the contract is not self-sufficient. Adding such a dependency here is how that happens.
   The enforcer's `bannedDependencies` rule (`IMP-M0-02`) fails `validate` if either appears, at any
   scope, so this doesn't rely on anyone remembering to check it by hand.
2. **Java 21 is the maximum, not the target.** Game code runs in Espresso as a Java 21 guest. A
   Java 22+ API compiles here and fails at run time inside a sandbox, which is the worst place to
   find out.
3. **Javadoc on every public type is enforced by Checkstyle**, not by review. This is the one
   repository that runs Checkstyle (`conventions.md` §2) — the Javadoc *is* the documentation a
   third-party developer gets.

Package root `io.turnbase.gdk`, with the sub-packages fixed by `backend-gdk-api.md` §2 —
`.context`, `.effect`, `.result`, `.model`. That layout is part of the published surface; renaming a
package is a breaking change. All five exist from `IMP-M0-02` even though four start empty, for
exactly that reason.

TypeScript source lives under `src/main/ts/` (tests under `src/test/ts/`), matching Maven's
`src/main/<lang>` shape rather than a conventional npm `src/`; `npm run build` etc. run from the
repository root.

**`IMP-M0-02` deliberately ships no `TurnBaseGame`, no context/result/effect types, and no
`TurnBaseClient`.** It is the build-and-publish floor only. The Java type surface is `IMP-M3-01`;
the frontend client is `IMP-M3-12`. If you're picking up either of those and something looks
missing, it isn't — that's the item you're on.

## What must not leak in

`backend-gdk-api.md` §2: interfaces, records, enums and builders **only** — no `main`, no I/O, no
serialization logic (FR-GDK-015). Serialization is the host's side of the boundary. If a type here
needs to know how it is encoded, the design has gone wrong.

## Before you commit

```bash
./mvnw spotless:apply
./mvnw verify        # includes Checkstyle, Error Prone and NullAway
npm run build && npm run typecheck
```

**`@NullMarked` here is part of the published contract** ([ADR-0015](../docs/decisions/0015-static-analysis-error-prone-nullaway.md)).
A third-party developer's IDE reads these annotations, so a nullable return that is not marked is a
documentation defect as much as a safety one — and changing a signature's nullability after release
is a breaking change like any other.

`git config core.hooksPath .githooks` once, to get the `pre-push` fast-fail.

`buf breaking` does not guard this repo — nothing here is generated from the proto. **The guard on a
breaking change is you.** Semantic versioning is real here in a way it is not elsewhere: a game
compiled against 1.2.0 must keep working.

## If a specification is wrong

**Stop, and fix the document in the parent repository first.** A defect published in this artifact
reaches people outside the project and cannot be quietly corrected.
