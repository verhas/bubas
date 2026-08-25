package javax0.bubas.bunit.commands;

import javax0.bubas.bunit.MatchesArguments;
import javax0.bubas.bunit.NamesTarget;
import javax0.bubas.bunit.Expectation;
import javax0.bubas.api.ExpressionArg;
import javax0.bubas.api.StatementContext;

import java.util.List;

/** {@code "APPROVE _" WAS CALLED WITH "o1"} */
@Expectation
@NamesTarget("name")
@MatchesArguments("a")
public final class ExpectCalledWith {

    public static final String PATTERN =
            "{literal/STRING:name} WAS CALLED WITH {expression:a}";

    public void call(StatementContext ctx, String name, ExpressionArg a) {
        Expectations.calledWith(ctx, name, List.of(a.evaluate()));
    }
}
