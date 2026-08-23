package javax0.bubas.support;

import javax0.bubas.api.BubasType;
import javax0.bubas.api.ExpressionArg;
import javax0.bubas.api.StatementContext;
import javax0.bubas.api.VariableArg;

/**
 * {@code DECLARE rate DECIMAL FINAL = 0.07}
 * <p>
 * Identical in body to the initialized form: finality is settled by the pattern, and the slot seals
 * itself after this one write.
 */
public final class DeclareFinal {

    public static final String PATTERN =
            "DECLARE {new > identifier/T:name > final} {type:T} FINAL = {expression/T:init}";

    public void call(StatementContext ctx, VariableArg name, BubasType type, ExpressionArg init) {
        name.set(init.evaluate());
    }
}
