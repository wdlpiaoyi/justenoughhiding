package com.wdlpiaoyi.justenoughhiding.client.viewer;

import com.wdlpiaoyi.justenoughhiding.intent.IntentTarget;

/**
 * One selectable candidate for the GUI's autocomplete, produced by
 * {@link ViewerAdapter#suggest}. {@code label} is plain text (no icons).
 */
public record TargetSuggestion(IntentTarget target, String label)
{
}
