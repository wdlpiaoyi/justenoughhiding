package com.wdlpiaoyi.justenoughhiding.jei;

import com.wdlpiaoyi.justenoughhiding.JustEnoughHiding;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IExtraIngredientRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;

@JeiPlugin
public final class JehJeiPlugin implements IModPlugin
{
    private static final ResourceLocation PLUGIN_UID = ResourceLocation.fromNamespaceAndPath(JustEnoughHiding.MODID, "main");

    private final JeiReveal reveal = new JeiReveal();

    @Override
    public ResourceLocation getPluginUid()
    {
        return PLUGIN_UID;
    }

    @Override
    public void registerExtraIngredients(IExtraIngredientRegistration registration)
    {
        reveal.offerExtraIngredients(registration);
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime)
    {
        reveal.activate(jeiRuntime);
    }

    @Override
    public void onRuntimeUnavailable()
    {
        reveal.deactivate();
    }
}
