package com.wdlpiaoyi.justenoughhiding.client.viewer;

import com.wdlpiaoyi.justenoughhiding.intent.IntentTarget;

/**
 * One selectable candidate for the GUI's autocomplete, produced by
 * {@link ViewerAdapter#suggest}. {@code label} is the plain-text row shown in the list,
 * while {@code completion} is the text filled into the editor when the candidate is accepted
 * or Tab-completed.
 */
public record TargetSuggestion(IntentTarget target, String label, String completion)
{
}
