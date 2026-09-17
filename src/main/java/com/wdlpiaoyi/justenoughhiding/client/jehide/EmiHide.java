package com.wdlpiaoyi.justenoughhiding.client.jehide;

/**
 * Placeholder for hiding {@link com.wdlpiaoyi.justenoughhiding.listehiding.ListEHiding} entries
 * in EMI (phase 2).
 * <p>
 * EMI has no runtime hide API: hiding is registered while EMI builds its registry
 * ({@code EmiRegistry.removeEmiStacks(Predicate)} / {@code removeRecipes(Predicate)}), and those
 * predicates are re-applied on every EMI reload. Phase 2 will register predicates that consult
 * the live list plus recorded hide-intents, and trigger {@code Minecraft.reloadResourcePacks()}
 * when the list changes. Nothing is implemented yet.
 */
public final class EmiHide
{
    private EmiHide()
    {
    }

    /**
     * @param registry the {@code dev.emi.emi.api.EmiRegistry}; typed as {@link Object} to keep
     *                 this class free of a hard EMI dependency.
     */
    public static void apply(Object registry)
    {
        // TODO(phase 2): registry.removeEmiStacks(...) / registry.removeRecipes(...).
    }

    public static void reapply()
    {
        // TODO(phase 2): debounce Minecraft.reloadResourcePacks() so EMI re-registers.
    }

    /** Consulted by the future EMI reveal mixins. */
    public static boolean isHidden(Object emiStack)
    {
        return false;
    }
}
