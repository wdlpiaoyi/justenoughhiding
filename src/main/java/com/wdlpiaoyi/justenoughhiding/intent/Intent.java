package com.wdlpiaoyi.justenoughhiding.intent;

public record Intent(IntentTarget target, IntentKind kind, IntentSource source, long sequence, int count)
{
}
