package javax0.bubas.support;

import javax0.bubas.api.BubasDescription;
import javax0.bubas.api.BubasStatic;
import javax0.bubas.api.Context;

/**
 * {@code TO_INTEGER(s) -> INTEGER}
 * <p>
 * Text to number is the one conversion BUBAS cannot do implicitly, because it can fail. Failing is
 * an error naming the text, not a zero: a script that silently treated {@code "twelve"} as nothing
 * would be worse than one that stopped.
 */
@BubasDescription("""
        Reads text as a whole number.
        Fails, naming the text, when it is not one — there is no silent zero, because a total
        that quietly became nothing is worse than a program that stopped.
        """)
@BubasStatic
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
