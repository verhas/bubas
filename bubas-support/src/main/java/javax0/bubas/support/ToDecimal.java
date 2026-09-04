package javax0.bubas.support;

import javax0.bubas.api.BubasDescription;
import javax0.bubas.api.BubasMemoizable;
import javax0.bubas.api.CoreContext;

import java.math.BigDecimal;

/** {@code TO_DECIMAL(s) -> DECIMAL} */
@BubasDescription("""
        Reads text as an exact decimal number, keeping the scale it was written with.
        """)
@BubasMemoizable
public final class ToDecimal {

    public static final String NAME = "TO_DECIMAL";

    public BigDecimal call(CoreContext ctx, String s) {
        try {
            return new BigDecimal(s.trim());
        } catch (NumberFormatException e) {
            ctx.error("'" + s + "' is not a DECIMAL");
            throw new IllegalStateException("unreachable: error() throws", e);
        }
    }
}
