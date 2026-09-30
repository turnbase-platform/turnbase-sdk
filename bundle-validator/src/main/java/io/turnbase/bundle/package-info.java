/**
 * The shared bundle validator ({@code bundle-format.md} §2.4, §3, §4): one archive reader, manifest
 * validator, backend JAR analyzer and static serializability analysis, consumed by the platform's
 * upload pipeline and the local test harness so the two cannot drift. Static inspection only —
 * nothing here classloads or executes archive bytes ({@code NFR-UPLOAD-002}). Entry point: {@link
 * io.turnbase.bundle.BundleValidator}.
 */
@NullMarked
package io.turnbase.bundle;

import org.jspecify.annotations.NullMarked;
