package javax0.bubas.support;

import javax0.bubas.api.BubasType;
import javax0.bubas.api.ExpressionArg;
import javax0.bubas.api.StatementContext;
import javax0.bubas.api.VariableArg;

/** {@code DECLARE total DECIMAL = 0.0} */
public final class DeclareInitialized {

    public static final String PATTERN =
            "DECLARE {new > identifier/T:name > initialized} {type:T} = {expression/T:init}";

    public void call(StatementContext ctx, VariableArg name, BubasType type, ExpressionArg init) {
        name.set(init.evaluate());
    }
}
