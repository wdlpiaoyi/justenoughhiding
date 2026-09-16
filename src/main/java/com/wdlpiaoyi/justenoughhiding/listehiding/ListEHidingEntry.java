package com.wdlpiaoyi.justenoughhiding.listehiding;

import com.wdlpiaoyi.justenoughhiding.intent.IntentTarget;

/** One entry of the locally persisted {@link ListEHiding}. Content is still provisional. */
public record ListEHidingEntry(IntentTarget target, boolean enabled, String note)
{
}
