package com.wdlpiaoyi.justenoughhiding.intent;

public record IngredientKey(String typeUid, String uid)
{
    public static IngredientKey of(String typeUid, String uid)
    {
        return new IngredientKey(typeUid, uid);
    }
}
