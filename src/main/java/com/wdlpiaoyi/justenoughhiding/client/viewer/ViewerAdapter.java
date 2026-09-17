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

    /**
     * Higher wins when more than one viewer is available. EMI outranks JEI because EMI takes
     * over the recipe-viewer overlay when it is installed.
     */
    default int priority()
    {
        return 0;
    }

    // ---- presentation (used by the intent viewer GUI) ----

    /**
     * Selectable target kinds for the editor's kind tabs, best-effort ordered. The first
     * {@code "auto"} entry must always be present. Implementations may enumerate whatever the
     * viewer knows about (ingredient types, recipes, categories, tags, ...).
     */
    default List<TargetKind> targetKinds()
    {
        return List.of(new TargetKind("", "Auto"));
    }

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
     * Used by the explicit {@code item <id>} prefix.
     */
    default IntentTarget ingredientTarget(String uid)
    {
        return null;
    }

    /**
     * Candidates matching {@code query} for the GUI autocomplete. {@code kind} filters by
     * {@link IntentTarget#kind()} and may be blank for "any". {@code query} is the raw editor
     * text; return at most {@code limit} entries, best matches first. Empty when nothing matches.
     */
    default List<TargetSuggestion> suggest(String query, String kind, int limit)
    {
        return List.of();
    }

    /**
     * Auto-detects the target kind from free user text (an item uid, a recipe id or a recipe
     * category uid). Returns {@code null} when the text is not recognised. Precedence is up to
     * the implementation.
     */
    default IntentTarget detect(String text)
    {
        return null;
    }

    /**
     * Builds a target of an explicit kind from an id. A blank {@code kind} means "auto" and
     * falls back to {@link #detect}. Returns {@code null} when the id cannot be resolved to
     * that kind.
     */
    default IntentTarget ofKind(String kind, String id)
    {
        return null;
    }

    /**
     * Concrete targets matched by a {@link IntentTarget.Pattern}, capped at {@code limit}.
     * Used to preview match counts and (later) to apply patterns.
     */
    default List<TargetSuggestion> matches(IntentTarget pattern, int limit)
    {
        return List.of();
    }

    /** Re-apply the current hide rules (ListEHiding + recorded hide-intents) to this viewer. */
    default void reapplyHides()
    {
    }

    /** Called every client tick; viewers can debounce expensive re-application here. */
    default void tickHides()
    {
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
