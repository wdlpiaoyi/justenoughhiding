package com.wdlpiaoyi.justenoughhiding.integration.emi;

import com.wdlpiaoyi.justenoughhiding.JustEnoughHiding;
import com.wdlpiaoyi.justenoughhiding.config.JehConfig;
import com.wdlpiaoyi.justenoughhiding.intent.IngredientKey;
import com.wdlpiaoyi.justenoughhiding.intent.IntentKind;
import com.wdlpiaoyi.justenoughhiding.intent.IntentRegistry;
import com.wdlpiaoyi.justenoughhiding.intent.IntentSource;
import com.wdlpiaoyi.justenoughhiding.intent.IntentSuppressor;
import com.wdlpiaoyi.justenoughhiding.intent.IntentTarget;
import com.wdlpiaoyi.justenoughhiding.intent.source.ModSourceResolver;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.registry.EmiStackList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;

import java.util.List;
import java.util.function.Predicate;

/**
 * Records EMI-native hide actions as intents, mirroring {@code JeiIntentRecorder}:
 * plugin stack removals ({@code EmiRegistry.removeEmiStacks}) and EMI edit-mode visibility
 * changes ({@code EmiHidden.setVisibility}). Only loaded when EMI is present.
 * <p>
 * Plugin removals are predicates, not concrete stacks. EMI's reload calls
 * {@code EmiStackList.reload()} (the raw list) <em>before</em> plugins register, so the predicate
 * can be evaluated right away.
 */
public final class EmiIntentRecorder
{
    private static final String ITEM_TYPE = "minecraft:item_stack";
    private static final String FLUID_TYPE = "fluid_stack";

    private EmiIntentRecorder()
    {
    }

    private static boolean disabled()
    {
        return !JehConfig.intentRecordingEnabled() || IntentSuppressor.suppressed();
    }

    /** Evaluates a {@code removeEmiStacks} predicate against the current raw stack list. */
    public static void recordRemovedStacks(Predicate<EmiStack> predicate)
    {
        if (disabled() || predicate == null)
        {
            return;
        }
        IntentSource source = currentSource();
        if (isSelf(source))
        {
            return;
        }

        List<EmiStack> stacks;
        try
        {
            stacks = EmiStackList.stacks;
        }
        catch (Throwable t)
        {
            return;
        }
        if (stacks == null)
        {
            return;
        }

        for (EmiStack stack : List.copyOf(stacks))
        {
            if (stack == null || stack.isEmpty())
            {
                continue;
            }
            boolean removed;
            try
            {
                removed = predicate.test(stack);
            }
            catch (Throwable t)
            {
                continue;
            }
            if (removed)
            {
                recordStack(stack, IntentKind.REMOVED, source);
            }
        }
    }

    public static void recordIngredient(EmiIngredient ingredient, IntentKind kind, IntentSource source)
    {
        if (disabled() || ingredient == null)
        {
            return;
        }
        IntentSource resolved = source != null ? source : currentSource();
        if (isSelf(resolved))
        {
            return;
        }
        for (EmiStack stack : stacksOf(ingredient))
        {
            recordStack(stack, kind, resolved);
        }
    }

    public static void removeIngredient(EmiIngredient ingredient, IntentSource source)
    {
        if (disabled() || ingredient == null)
        {
            return;
        }
        IntentSource resolved = source != null ? source : currentSource();
        if (isSelf(resolved))
        {
            return;
        }
        for (EmiStack stack : stacksOf(ingredient))
        {
            IntentTarget target = targetOf(stack);
            if (target != null)
            {
                IntentRegistry.remove(target, resolved.id());
            }
        }
    }

    private static void recordStack(EmiStack stack, IntentKind kind, IntentSource source)
    {
        IntentTarget target = targetOf(stack);
        if (target != null)
        {
            IntentRegistry.record(target, kind, source);
        }
    }

    private static IntentTarget targetOf(EmiStack stack)
    {
        if (stack == null || stack.isEmpty())
        {
            return null;
        }
        try
        {
            String typeUid;
            Object key = stack.getKey();
            if (key instanceof Fluid)
            {
                typeUid = FLUID_TYPE;
            }
            else if (!stack.getItemStack().isEmpty())
            {
                typeUid = ITEM_TYPE;
            }
            else
            {
                return null;
            }
            ResourceLocation id = stack.getId();
            return id == null ? null : IntentTarget.of(IngredientKey.of(typeUid, id.toString()));
        }
        catch (Throwable t)
        {
            return null;
        }
    }

    private static List<EmiStack> stacksOf(EmiIngredient ingredient)
    {
        try
        {
            List<EmiStack> stacks = ingredient.getEmiStacks();
            return stacks == null ? List.of() : stacks;
        }
        catch (Throwable t)
        {
            return List.of();
        }
    }

    private static IntentSource currentSource()
    {
        return ModSourceResolver.sourceOfCaller();
    }

    private static boolean isSelf(IntentSource source)
    {
        return source != null && JustEnoughHiding.MODID.equals(source.id());
    }
}
