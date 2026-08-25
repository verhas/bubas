package javax0.bubas.bunit.matchers;

import javax0.bubas.api.Context;
import javax0.bubas.bunit.Matcher;

import java.math.BigDecimal;

/** {@code GREATER_THAN(100)} — strictly above. Its inclusive twin is {@code AT_LEAST}. */
public final class GreaterThan {

    public static final String NAME = "GREATER_THAN";

    public Matcher call(Context ctx, BigDecimal limit) {
        return new Threshold("greater than", limit, comparison -> comparison > 0);
    }
}
