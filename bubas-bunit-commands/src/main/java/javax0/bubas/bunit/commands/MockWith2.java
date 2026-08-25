package javax0.bubas.bunit.commands;

import javax0.bubas.api.ExpressionArg;
import javax0.bubas.api.StatementContext;

import java.util.List;

/**
 * {@code "RATE_FOR" WITH "EU", 42 RETURNS 0.07}
 * <p>
 * One pattern per arity: an expression stops at a comma, so a single placeholder cannot absorb an
 * argument list.
 */
public final class MockWith2 {

    public static final String PATTERN =
            "{literal/STRING:name} WITH {expression:a}, {expression:b} RETURNS {expression:value}";

    public void call(StatementContext ctx, String name, ExpressionArg a, ExpressionArg b,
                     ExpressionArg value) {
        Mock.recorder(ctx).mockFunction(name, List.of(a.evaluate(), b.evaluate()), value.evaluate());
    }
}
