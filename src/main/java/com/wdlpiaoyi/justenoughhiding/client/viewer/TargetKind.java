package com.wdlpiaoyi.justenoughhiding.client.viewer;

/**
 * One entry of the target editor's kind selector. {@code key} is passed back to
 * {@link ViewerAdapter#suggest} and {@link ViewerAdapter#ofKind}; a blank key means "auto".
 */
public record TargetKind(String key, String label)
{
}
