package com.wdlpiaoyi.justenoughhiding.client.viewer;

import com.wdlpiaoyi.justenoughhiding.client.gui.column.Column;
import com.wdlpiaoyi.justenoughhiding.intent.Intent;
import com.wdlpiaoyi.justenoughhiding.intent.IntentTarget;

import java.util.List;

/**
 * The single place where the GUI and management layer talk to a recipe viewer (JEI now, EMI later).
 * <p>
 * Implementations are client-side. {@link Adapters} picks the first {@link #available()} adapter.
 */
public interface ViewerAdapter
{
    String id();

    boolean available();

    // ---- presentation (used by the intent viewer GUI) ----

    List<Column<Intent>> columns();

    IconRenderer icon(IntentTarget target);

    default String displayText(IntentTarget target)
    {
        return target.describe();
    }

    boolean isBookmarkKey(int keyCode, int scanCode);

    boolean bookmark(IntentTarget target);

    /**
     * Builds an ingredient target from raw user text, e.g. {@code "minecraft:stone"} or
     * {@code "minecraft:stone{Enchantments:[{}]}"}. Returns {@code null} when the text cannot
     * be understood, so the GUI can stay independent of any viewer's ingredient types.
     */
    default IntentTarget ingredientTarget(String uid)
    {
        return null;
    }

    // ---- lifecycle / management (reveal here, hide later, EMI later) ----

    default void onRuntimeAvailable(Object runtime)
    {
    }

    default void onRuntimeUnavailable()
    {
    }

    default void reveal(IntentTarget target)
    {
    }

    default void hide(IntentTarget target)
    {
    }
}
