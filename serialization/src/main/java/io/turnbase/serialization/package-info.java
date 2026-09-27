/**
 * The shared serialization engine (game-host-internals.md §3.6, §5 — GRMP-F4): the platform {@code
 * ObjectMapper} configuration, the serialize/deserialize round-trip routine and the
 * NFR-SIZE-001/002 limit checks. Reused by the Game Host's guest bridge, the local test harness and
 * the upload validator, so the three run the identical engine.
 */
@NullMarked
package io.turnbase.serialization;

import org.jspecify.annotations.NullMarked;
