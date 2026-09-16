package com.wdlpiaoyi.justenoughhiding.mixin.jei;

import com.wdlpiaoyi.justenoughhiding.client.jehide.JehEditMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "mezz.jei.common.config.ClientToggleState", remap = false)
public class ClientToggleStateMixin
{
    @Inject(method = "<init>", at = @At("RETURN"), remap = false, require = 0)
    private void jeh$capture(CallbackInfo ci)
    {
        JehEditMode.setToggleState(this);
    }
}
