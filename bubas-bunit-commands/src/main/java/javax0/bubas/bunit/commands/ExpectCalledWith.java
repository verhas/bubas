package javax0.bubas.bunit.commands;

import javax0.bubas.api.ExpressionArg;
import javax0.bubas.api.StatementContext;
import javax0.bubas.bunit.CountsArguments;
import javax0.bubas.bunit.Expectation;
import javax0.bubas.bunit.NamesTarget;
import javax0.bubas.bunit.matchers.Arguments;

/**
 * {@code "LOG_EVENT _, _" WAS CALLED WITH ARGS("INFO", CONTAINS("over limit"))}
 * <p>
 * Any number of arguments, and any of them may be a matcher rather than a value — which is what
 * lets an expectation say what matters about a call without pinning what does not.
 */
@Expectation
@NamesTarget("name")
@CountsArguments("args")
public final class ExpectCalledWith {

    public static final String PATTERN =
            "{literal/STRING:name} WAS CALLED WITH {expression/Arguments:args}";

    public void call(StatementContext ctx, String name, ExpressionArg args) {
        Expectations.calledWith(ctx, name, args.evaluate().as(Arguments.class).values());
    }
}
