package com.wdlpiaoyi.justenoughhiding.mixin.jei.intent;

import com.wdlpiaoyi.justenoughhiding.jei.intent.PluginContext;
import mezz.jei.api.IModPlugin;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.function.Consumer;

/**
 * EMI's {@code jei.PluginCallerMixin} redirects the same {@code Consumer.accept} call and, because
 * mods with an EMI plugin are considered "handled by EMI", it <b>skips all JEI plugin callbacks for
 * them</b> — including JEH itself (JEH registers an EMI plugin). That would stop JEH's JEI reveal,
 * scanner and intent recording whenever EMI is installed.
 * <p>
 * Instead of fighting the redirect, this injects at the head of {@code callOnPlugins}: for the
 * runtime phases, JEH's own plugin is invoked manually through JEI's consumer (EMI will skip it in
 * the normal loop). Only active when EMI is present, so JEI-only setups are unaffected. It also
 * restores {@link PluginContext} around JEH's own callback.
 */
@Pseudo
@Mixin(targets = "mezz.jei.library.load.PluginCaller", remap = false)
public class PluginCallerContextMixin
{
    private static final String SEND_RUNTIME = "Sending Runtime";
    private static final String SEND_RUNTIME_UNAVAILABLE = "Sending Runtime Unavailable";

    @Inject(method = "callOnPlugins", at = @At("HEAD"), remap = false, require = 0)
    private static void jeh$ensureSelfCalled(
        String title,
        List<IModPlugin> plugins,
        Consumer<IModPlugin> func,
        CallbackInfo ci
    )
    {
        if (!SEND_RUNTIME.equals(title) && !SEND_RUNTIME_UNAVAILABLE.equals(title))
        {
            return;
        }
        ModList modList = ModList.get();
        if (modList == null || !modList.isLoaded("emi"))
        {
            return;
        }
        for (IModPlugin plugin : plugins)
        {
            if (isSelf(plugin))
            {
                PluginContext.push(plugin);
                try
                {
                    func.accept(plugin);
                }
                finally
                {
                    PluginContext.pop();
                }
            }
        }
    }

    private static boolean isSelf(IModPlugin plugin)
    {
        try
        {
            ResourceLocation uid = plugin.getPluginUid();
            return uid != null && "justenoughhiding".equals(uid.getNamespace());
        }
        catch (Throwable t)
        {
            return false;
        }
    }
}
