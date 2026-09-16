package com.wdlpiaoyi.justenoughhiding.client.viewer;

import com.wdlpiaoyi.justenoughhiding.intent.IntentTarget;

import java.util.regex.PatternSyntaxException;

/** Matches concrete targets against a {@link IntentTarget.Pattern} (glob or regex). */
public final class TargetMatcher
{
    private TargetMatcher()
    {
    }

    public static boolean matches(IntentTarget.Pattern pattern, IntentTarget concrete)
    {
        if (pattern == null || concrete == null || concrete instanceof IntentTarget.Pattern)
        {
            return false;
        }
        String scope = pattern.scope();
        if (scope != null && !scope.isEmpty() && !scope.equals(TargetKeys.kindKey(concrete)))
        {
            return false;
        }
        return matchesId(pattern, TargetKeys.id(concrete));
    }

    public static boolean matchesId(IntentTarget.Pattern pattern, String id)
    {
        if (pattern == null || id == null)
        {
            return false;
        }
        java.util.regex.Pattern compiled = compile(pattern);
        return compiled != null && compiled.matcher(id).matches();
    }

    /** Compiled case-insensitive matcher, or null when the pattern is empty/invalid. */
    public static java.util.regex.Pattern compile(IntentTarget.Pattern pattern)
    {
        if (pattern == null || pattern.pattern() == null || pattern.pattern().isEmpty())
        {
            return null;
        }
        try
        {
            String regex = pattern.mode() == IntentTarget.MatchMode.REGEX
                ? pattern.pattern()
                : globToRegex(pattern.pattern());
            return java.util.regex.Pattern.compile(regex, java.util.regex.Pattern.CASE_INSENSITIVE);
        }
        catch (PatternSyntaxException t)
        {
            return null;
        }
        catch (Throwable t)
        {
            return null;
        }
    }

    private static String globToRegex(String glob)
    {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < glob.length(); i++)
        {
            char c = glob.charAt(i);
            switch (c)
            {
                case '*' -> result.append(".*");
                case '?' -> result.append('.');
                default -> result.append(java.util.regex.Pattern.quote(String.valueOf(c)));
            }
        }
        return result.toString();
    }
}
