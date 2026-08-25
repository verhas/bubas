package javax0.bubas.bunit.matchers;

import javax0.bubas.api.Context;
import javax0.bubas.bunit.Matcher;

import java.math.BigDecimal;

/** {@code AT_MOST(100)} — at or below. */
public final class AtMost {

    public static final String NAME = "AT_MOST";

    public Matcher call(Context ctx, BigDecimal limit) {
        return new Threshold("at most", limit, comparison -> comparison <= 0);
    }
}
