package com.wdlpiaoyi.justenoughhiding.integration.emi;

import com.wdlpiaoyi.justenoughhiding.client.jehide.EmiHide;
import com.wdlpiaoyi.justenoughhiding.client.viewer.Adapters;
import com.wdlpiaoyi.justenoughhiding.client.viewer.emi.EmiAdapter;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;

/**
 * EMI entrypoint ({@code @EmiEntrypoint}); EMI discovers and instantiates it only when EMI is
 * installed. Registers {@link EmiAdapter} so JEH uses EMI as its recipe viewer.
 */
@EmiEntrypoint
public final class JehEmiPlugin implements EmiPlugin
{
    private final EmiAdapter adapter = new EmiAdapter();

    public JehEmiPlugin()
    {
        Adapters.register(adapter);
    }

    @Override
    public void register(EmiRegistry registry)
    {
        adapter.invalidateIndex();
        EmiHide.apply(registry);
    }
}
