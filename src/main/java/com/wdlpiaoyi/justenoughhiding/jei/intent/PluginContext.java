package com.wdlpiaoyi.justenoughhiding.jei.intent;

import mezz.jei.api.IModPlugin;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayDeque;
import java.util.Deque;

public final class PluginContext
{
    private static final String NO_ID = "";

    private static final ThreadLocal<Deque<String>> STACK = ThreadLocal.withInitial(ArrayDeque::new);

    private PluginContext()
    {
    }

    public static void push(IModPlugin plugin)
    {
        String id = pluginId(plugin);
        STACK.get().push(id == null ? NO_ID : id);
    }

    public static void pop()
    {
        Deque<String> stack = STACK.get();
        if (!stack.isEmpty())
        {
            stack.pop();
        }
    }

    public static String currentId()
    {
        Deque<String> stack = STACK.get();
        if (stack.isEmpty())
        {
            return null;
        }
        String id = stack.peek();
        return NO_ID.equals(id) ? null : id;
    }

    private static String pluginId(IModPlugin plugin)
    {
        try
        {
            ResourceLocation uid = plugin.getPluginUid();
            return uid == null ? null : uid.getNamespace();
        }
        catch (Throwable t)
        {
            return null;
        }
    }
}
