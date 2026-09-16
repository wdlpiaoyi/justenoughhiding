package com.wdlpiaoyi.justenoughhiding.integration.kubejs;

import dev.latvian.mods.kubejs.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingsEvent;
import dev.latvian.mods.kubejs.script.ScriptType;

/**
 * KubeJS client plugin: exposes the {@code JEH} binding to {@code kubejs/client_scripts} so
 * scripts can read and modify the hiding list. Discovered through {@code kubejs.plugins.txt};
 * only loaded when KubeJS is installed.
 */
public final class JehKubeJSPlugin extends KubeJSPlugin
{
    @Override
    public void registerBindings(BindingsEvent event)
    {
        if (event.getType() == ScriptType.CLIENT)
        {
            event.add("JEH", new JehKubeJSBindings());
        }
    }
}
