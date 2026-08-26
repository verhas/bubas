package javax0.bubas.support;

import javax0.bubas.api.BubasDescription;
import javax0.bubas.api.BubasType;
import javax0.bubas.api.ExpressionArg;
import javax0.bubas.api.StatementContext;
import javax0.bubas.api.VariableArg;

/** {@code DECLARE total DECIMAL = 0.0} */
@BubasDescription("""
        Brings a variable into existence with a value already in it.
        The ordinary way to declare something whose starting value is known, and the only way to
        avoid the analyser complaining that a later read might come before the first assignment.
        """)
public final class DeclareInitialized {

    public static final String PATTERN =
            "DECLARE {new > identifier/T:name > initialized} {type:T} = {expression/T:init}";

    public void call(StatementContext ctx, VariableArg name, BubasType type, ExpressionArg init) {
        name.set(init.evaluate());
    }
}
