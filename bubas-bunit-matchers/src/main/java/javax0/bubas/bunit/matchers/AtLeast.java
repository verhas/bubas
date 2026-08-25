package javax0.bubas.bunit.matchers;

import javax0.bubas.api.Context;
import javax0.bubas.bunit.Matcher;

import java.math.BigDecimal;

/**
 * {@code AT_LEAST(100)} — at or above.
 * <p>
 * Both spellings exist because inclusive against exclusive is the classic off-by-one in a business
 * rule: "over the limit" is exactly this distinction, and a test that cannot say which it means
 * cannot catch the case where the program got it wrong.
 */
public final class AtLeast {

    public static final String NAME = "AT_LEAST";

    public Matcher call(Context ctx, BigDecimal limit) {
        return new Threshold("at least", limit, comparison -> comparison >= 0);
    }
}
