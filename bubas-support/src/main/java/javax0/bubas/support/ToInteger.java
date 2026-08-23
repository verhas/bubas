package javax0.bubas.support;

import javax0.bubas.api.Context;

/**
 * {@code TO_INTEGER(s) -> INTEGER}
 * <p>
 * Text to number is the one conversion BUBAS cannot do implicitly, because it can fail. Failing is
 * an error naming the text, not a zero: a script that silently treated {@code "twelve"} as nothing
 * would be worse than one that stopped.
 */
public final class ToInteger {

    public static final String NAME = "TO_INTEGER";

    public long call(Context ctx, String s) {
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            ctx.error("'" + s + "' is not an INTEGER");
            throw new IllegalStateException("unreachable: error() throws", e);
        }
    }
}
