package com.wdlpiaoyi.justenoughhiding.jei.intent;

import com.wdlpiaoyi.justenoughhiding.JustEnoughHiding;
import com.wdlpiaoyi.justenoughhiding.config.JehConfig;
import com.wdlpiaoyi.justenoughhiding.intent.IngredientKey;
import com.wdlpiaoyi.justenoughhiding.intent.IntentKind;
import com.wdlpiaoyi.justenoughhiding.intent.IntentRegistry;
import com.wdlpiaoyi.justenoughhiding.intent.IntentSource;
import com.wdlpiaoyi.justenoughhiding.intent.IntentTarget;
import com.wdlpiaoyi.justenoughhiding.intent.source.ModSourceResolver;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;

public final class JeiIntentRecorder
{
    private JeiIntentRecorder()
    {
    }

    public static boolean disabled()
    {
        return !JehConfig.intentRecordingEnabled();
    }

    private static final ThreadLocal<Integer> SUPPRESS = ThreadLocal.withInitial(() -> 0);

    /** Run an action without recording the JEI changes it makes (used by JEHide / reveal). */
    public static void runSuppressed(Runnable action)
    {
        SUPPRESS.set(SUPPRESS.get() + 1);
        try
        {
            action.run();
        }
        finally
        {
            int depth = SUPPRESS.get() - 1;
            if (depth <= 0)
            {
                SUPPRESS.remove();
            }
            else
            {
                SUPPRESS.set(depth);
            }
        }
    }

    private static boolean skip()
    {
        return SUPPRESS.get() > 0 || disabled();
    }

    public static IntentSource currentSource()
    {
        String id = PluginContext.currentId();
        if (id != null && !id.isBlank())
        {
            return IntentSource.mod(id);
        }
        return ModSourceResolver.sourceOfCaller();
    }

    private static boolean isSelf(IntentSource source)
    {
        return source != null && JustEnoughHiding.MODID.equals(source.id());
    }

    @SuppressWarnings("unchecked")
    public static void recordIngredients(
        IIngredientManager manager,
        IIngredientType<?> type,
        Collection<?> ingredients,
        IntentKind kind
    )
    {
        if (skip() || manager == null || type == null || ingredients == null || ingredients.isEmpty())
        {
            return;
        }
        IntentSource source = currentSource();
        if (isSelf(source))
        {
            return;
        }

        IIngredientHelper<Object> helper;
        try
        {
            helper = (IIngredientHelper<Object>) manager.getIngredientHelper((IIngredientType<Object>) type);
        }
        catch (Throwable t)
        {
            return;
        }

        String typeUid = type.getUid();
        for (Object ingredient : ingredients)
        {
            if (ingredient == null)
            {
                continue;
            }
            String uid;
            try
            {
                uid = helper.getUniqueId(ingredient, UidContext.Ingredient);
            }
            catch (Throwable t)
            {
                continue;
            }
            if (uid != null)
            {
                IntentRegistry.record(IntentTarget.of(IngredientKey.of(typeUid, uid)), kind, source);
            }
        }
    }

    public static void recordTyped(
        IIngredientManager manager,
        ITypedIngredient<?> typed,
        IntentKind kind,
        IntentSource fixedSource
    )
    {
        if (skip() || manager == null || typed == null)
        {
            return;
        }
        IntentSource source = fixedSource != null ? fixedSource : currentSource();
        if (isSelf(source))
        {
            return;
        }
        IntentTarget target = targetOf(manager, typed);
        if (target != null)
        {
            IntentRegistry.record(target, kind, source);
        }
    }

    /**
     * Forget a previously recorded intent for this ingredient from the given source.
     * Used when an action simply returns an ingredient to its default state,
     * e.g. un-hiding via JEI edit mode: "not hidden" is the default, so no entry is kept.
     */
    public static void removeTyped(IIngredientManager manager, ITypedIngredient<?> typed, IntentSource fixedSource)
    {
        if (skip() || manager == null || typed == null)
        {
            return;
        }
        IntentSource source = fixedSource != null ? fixedSource : currentSource();
        if (isSelf(source))
        {
            return;
        }
        IntentTarget target = targetOf(manager, typed);
        if (target != null)
        {
            IntentRegistry.remove(target, source.id());
        }
    }

    @SuppressWarnings("unchecked")
    private static IntentTarget targetOf(IIngredientManager manager, ITypedIngredient<?> typed)
    {
        try
        {
            IIngredientType<Object> type = (IIngredientType<Object>) typed.getType();
            IIngredientHelper<Object> helper = (IIngredientHelper<Object>) manager.getIngredientHelper(type);
            String uid = helper.getUniqueId(typed.getIngredient(), UidContext.Ingredient);
            if (uid == null)
            {
                return null;
            }
            return IntentTarget.of(IngredientKey.of(type.getUid(), uid));
        }
        catch (Throwable t)
        {
            return null;
        }
    }

    public static void recordRecipe(ResourceLocation recipeTypeUid, Object recipe, IntentKind kind)
    {
        if (skip() || recipeTypeUid == null || recipe == null)
        {
            return;
        }
        IntentSource source = currentSource();
        if (isSelf(source))
        {
            return;
        }
        IntentRegistry.record(IntentTarget.of(recipeTypeUid, String.valueOf(recipe)), kind, source);
    }

    public static void recordRecipeCategory(ResourceLocation recipeTypeUid, IntentKind kind)
    {
        if (skip() || recipeTypeUid == null)
        {
            return;
        }
        IntentSource source = currentSource();
        if (isSelf(source))
        {
            return;
        }
        IntentRegistry.record(IntentTarget.category(recipeTypeUid), kind, source);
    }
}
