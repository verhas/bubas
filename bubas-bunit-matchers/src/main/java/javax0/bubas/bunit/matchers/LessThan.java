package javax0.bubas.bunit.matchers;

import javax0.bubas.api.Context;
import javax0.bubas.bunit.Matcher;

import java.math.BigDecimal;

/** {@code LESS_THAN(100)} — strictly below. Its inclusive twin is {@code AT_MOST}. */
public final class LessThan {

    public static final String NAME = "LESS_THAN";

    public Matcher call(Context ctx, BigDecimal limit) {
        return new Threshold("less than", limit, comparison -> comparison < 0);
    }
}
