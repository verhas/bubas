package javax0.bubas.bunit.commands;

import javax0.bubas.api.ExpressionArg;
import javax0.bubas.api.StatementContext;

/** {@code ARGUMENT "orderId" IS 42} — supplies one of the subject's parameters. */
public final class Argument {

    public static final String PATTERN = "ARGUMENT {literal/STRING:name} IS {expression:value}";

    public void call(StatementContext ctx, String name, ExpressionArg value) {
        Mock.recorder(ctx).argument(name, value.evaluate());
    }
}
