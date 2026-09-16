package com.wdlpiaoyi.justenoughhiding.client.gui;

import com.wdlpiaoyi.justenoughhiding.intent.Intent;

public final class IntentFormat
{
    private IntentFormat()
    {
    }

    public static String line(Intent intent)
    {
        return intent.kind() + "\t" + intent.source().id() + "\tx" + intent.count() + "\t" + intent.target().describe();
    }
}
