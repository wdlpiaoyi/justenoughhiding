package com.wdlpiaoyi.justenoughhiding.intent;

public record IntentSource(String id, Type type)
{
    public enum Type
    {
        MOD,
        JEI_EDIT_MODE,
        EMI_EDIT_MODE,
        TAG,
        SERVER,
        UNKNOWN
    }

    public static final IntentSource JEI_EDIT_MODE = new IntentSource("JEI edit mode", Type.JEI_EDIT_MODE);
    public static final IntentSource EMI_EDIT_MODE = new IntentSource("EMI edit mode", Type.EMI_EDIT_MODE);
    public static final IntentSource TAG = new IntentSource("tag", Type.TAG);
    public static final IntentSource SERVER = new IntentSource("server", Type.SERVER);
    public static final IntentSource UNKNOWN = new IntentSource("unknown", Type.UNKNOWN);

    public static IntentSource mod(String modId)
    {
        if (modId == null || modId.isBlank())
        {
            return UNKNOWN;
        }
        return new IntentSource(modId, Type.MOD);
    }
}
