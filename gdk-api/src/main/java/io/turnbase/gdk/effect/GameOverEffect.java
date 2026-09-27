package io.turnbase.gdk.effect;

import io.turnbase.gdk.model.GameOutcome;

/**
 * Game end. Terminal and single per session (FR-EFF-005/006/007). After this effect is processed
 * the session ends; later effects in the <em>same</em> result list are still applied first, then
 * the session terminates (FR-EFF-006). Only the first {@code gameOver} across the session is
 * honored (FR-EFF-007).
 *
 * @param outcome the structured outcome for platform use (game history, statistics); not delivered
 *     to the FE
 * @param resultsPayload an opaque, developer-typed, non-null payload for the game FE serialized to
 *     JSON by the GDK Host; at most 10 MB serialized (NFR-SIZE-002)
 */
public record GameOverEffect(GameOutcome outcome, Object resultsPayload) implements Effect {}
