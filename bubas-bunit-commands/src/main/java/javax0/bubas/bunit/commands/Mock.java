package javax0.bubas.bunit.commands;

import javax0.bubas.bunit.DeclaresMock;
import javax0.bubas.bunit.NamesTarget;
import javax0.bubas.bunit.MockRecorder;
import javax0.bubas.api.Context;
import javax0.bubas.api.ExpressionArg;
import javax0.bubas.api.StatementContext;

import java.util.List;

/**
 * {@code "ORDER_TOTAL" RETURNS 1500.00}
 * <p>
 * Answers whatever the arguments are. The argument-specific form is {@link MockWith}.
 */
@NamesTarget("name")
@DeclaresMock
public final class Mock {

    public static final String PATTERN = "{literal/STRING:name} RETURNS {expression:value}";

    public void call(StatementContext ctx, String name, ExpressionArg value) {
        recorder(ctx).mockFunction(name, List.of(), value.evaluate());
    }

    static MockRecorder recorder(Context ctx) {
        return ctx.service(MockRecorder.class);
    }
}
