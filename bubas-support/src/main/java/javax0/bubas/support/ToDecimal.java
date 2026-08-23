package javax0.bubas.support;

import javax0.bubas.api.Context;

import java.math.BigDecimal;

/** {@code TO_DECIMAL(s) -> DECIMAL} */
public final class ToDecimal {

    public static final String NAME = "TO_DECIMAL";

    public BigDecimal call(Context ctx, String s) {
        try {
            return new BigDecimal(s.trim());
        } catch (NumberFormatException e) {
            ctx.error("'" + s + "' is not a DECIMAL");
            throw new IllegalStateException("unreachable: error() throws", e);
        }
    }
}
