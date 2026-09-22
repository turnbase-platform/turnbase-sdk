package io.turnbase.gdk.effect;

/**
 * A platform-managed game-log entry. Text supports the basic Markdown of FR-LOG-002 (bold, italic,
 * strikethrough, inline code). Rich color annotation syntax is deferred ({@code game-contracts.md}
 * OQ-001). (FR-EFF-004)
 *
 * @param text the log entry text
 */
public record LogEffect(String text) implements Effect {}
