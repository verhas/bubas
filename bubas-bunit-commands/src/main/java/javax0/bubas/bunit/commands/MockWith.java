package javax0.bubas.bunit.commands;

import javax0.bubas.api.ExpressionArg;
import javax0.bubas.api.StatementContext;
import javax0.bubas.bunit.CountsArguments;
import javax0.bubas.bunit.DeclaresMock;
import javax0.bubas.bunit.NamesTarget;
import javax0.bubas.bunit.SuppliesResult;
import javax0.bubas.bunit.matchers.Arguments;

/**
 * {@code "LOAD_ORDER" WITH ARGS(42) RETURNS "o1"}
 * <p>
 * One pattern for every arity. It used to be one pattern per count, which was a ceiling rather than
 * a design: a three-argument function simply could not be mocked. The argument list is a value now,
 * and because {@code Arguments} is an opaque type the type checker enforces the form — nothing but
 * a call to {@code ARGS} can produce one.
 * <p>
 * A matcher may stand in for any argument, so {@code ARGS(BETWEEN(100, 500))} answers a range.
 */
@NamesTarget("name")
@DeclaresMock
@CountsArguments("args")
@SuppliesResult("value")
public final class MockWith {

    public static final String PATTERN =
            "{literal/STRING:name} WITH {expression/Arguments:args} RETURNS {expression:value}";

    public void call(StatementContext ctx, String name, ExpressionArg args, ExpressionArg value) {
        Mock.recorder(ctx).mockFunction(name,
                args.evaluate().as(Arguments.class).values(), value.evaluate());
    }
}
