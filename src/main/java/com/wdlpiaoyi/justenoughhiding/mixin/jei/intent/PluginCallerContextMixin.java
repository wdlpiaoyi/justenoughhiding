package com.wdlpiaoyi.justenoughhiding.mixin.jei.intent;

import com.wdlpiaoyi.justenoughhiding.jei.intent.PluginContext;
import mezz.jei.api.IModPlugin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;
import java.util.function.Consumer;

@Pseudo
@Mixin(targets = "mezz.jei.library.load.PluginCaller", remap = false)
public class PluginCallerContextMixin
{
    @Redirect(
        method = "callOnPlugins",
        at = @At(value = "INVOKE", target = "java/util/function/Consumer.accept(Ljava/lang/Object;)V"),
        remap = false,
        require = 0
    )
    private static void jeh$withPluginContext(
        Consumer<IModPlugin> target,
        Object value,
        String title,
        List<IModPlugin> plugins,
        Consumer<IModPlugin> func
    )
    {
        IModPlugin plugin = (IModPlugin) value;
        PluginContext.push(plugin);
        try
        {
            target.accept(plugin);
        }
        finally
        {
            PluginContext.pop();
        }
    }
}
