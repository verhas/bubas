package javax0.bubas.support;

import javax0.bubas.api.BubasType;
import javax0.bubas.api.StatementContext;
import javax0.bubas.api.VariableArg;

/**
 * {@code DECLARE count INTEGER}
 * <p>
 * The body is empty, and correctly so. A placeholder that creates a variable carries a type
 * constraint, so name, type and finality are fixed before the handler runs and the runtime has
 * already made the slot. With a {@code declared} postcondition there is no value to supply, which
 * leaves nothing for this command to do.
 */
public final class Declare {

    public static final String PATTERN = "DECLARE {new > identifier/T:name > declared} {type:T}";

    public void call(StatementContext ctx, VariableArg name, BubasType type) {
    }
}
