package com.wdlpiaoyi.justenoughhiding.intent;

public enum IntentKind
{
    REMOVED(true, false),
    ADDED(false, true),
    HIDDEN(true, false),
    SHOWN(false, true),
    EDIT_MODE_HIDDEN(true, false),
    EDIT_MODE_SHOWN(false, true),
    TAG_HIDDEN(true, false),
    ABSENT_FROM_JEI(true, false),
    SERVER_MISSING(true, false),
    RECIPE_HIDDEN(true, false),
    RECIPE_SHOWN(false, true),
    RECIPE_CATEGORY_HIDDEN(true, false),
    RECIPE_CATEGORY_SHOWN(false, true);

    private final boolean hide;
    private final boolean show;

    IntentKind(boolean hide, boolean show)
    {
        this.hide = hide;
        this.show = show;
    }

    public boolean isHide()
    {
        return this.hide;
    }

    public boolean isShow()
    {
        return this.show;
    }
}
