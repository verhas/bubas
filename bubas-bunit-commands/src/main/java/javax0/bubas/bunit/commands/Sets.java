package javax0.bubas.bunit.commands;

import javax0.bubas.api.ExpressionArg;
import javax0.bubas.api.StatementContext;
import javax0.bubas.bunit.NamesTarget;
import javax0.bubas.bunit.SuppliesVariable;

/**
 * {@code "COUNT ORDERS INTO _ FOR _" SETS "total" TO 42}
 * <p>
 * A mocked command's handler never runs, so a variable its pattern writes stays unwritten and the
 * script reads an unassigned slot. An opaque target the framework fills with a token; anything else
 * has to be said, because inventing a zero or an empty string would be the silent default this
 * language refuses everywhere else.
 * <p>
 * A separate statement rather than a longer {@code IS MOCKED SETTING …} form: a pattern may write
 * more than one variable, and two lines compose where one line would need a variant per count.
 */
@NamesTarget("name")
@SuppliesVariable("variable")
public final class Sets {

    public static final String PATTERN =
            "{literal/STRING:name} SETS {literal/STRING:variable} TO {expression:value}";

    public void call(StatementContext ctx, String name, String variable, ExpressionArg value) {
        Mock.recorder(ctx).supplyVariable(name, variable, value.evaluate());
    }
}
