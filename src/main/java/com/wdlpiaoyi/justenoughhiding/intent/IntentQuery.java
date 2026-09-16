package com.wdlpiaoyi.justenoughhiding.intent;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public interface IntentQuery
{
    Collection<Intent> all();

    Collection<Intent> forIngredient(IngredientKey key);

    Collection<Intent> forItem(Item item);

    Collection<Intent> forRecipe(ResourceLocation recipeType, String recipeId);

    Collection<Intent> bySource(String sourceId);

    Collection<Intent> byKind(IntentKind kind);

    Set<IngredientKey> currentlyHidden();

    Map<IngredientKey, Set<String>> hiddenSources();

    void addListener(Consumer<Intent> listener);
}
