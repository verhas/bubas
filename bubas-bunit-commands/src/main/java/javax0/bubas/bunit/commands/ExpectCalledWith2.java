package javax0.bubas.bunit.commands;

import javax0.bubas.bunit.MatchesArguments;
import javax0.bubas.bunit.NamesTarget;
import javax0.bubas.bunit.Expectation;
import javax0.bubas.api.ExpressionArg;
import javax0.bubas.api.StatementContext;

import java.util.List;

/** {@code "LOG_EVENT _, _" WAS CALLED WITH "INFO", "over limit"} */
@Expectation
@NamesTarget("name")
@MatchesArguments({"a", "b"})
public final class ExpectCalledWith2 {

    public static final String PATTERN =
            "{literal/STRING:name} WAS CALLED WITH {expression:a}, {expression:b}";

    public void call(StatementContext ctx, String name, ExpressionArg a, ExpressionArg b) {
        Expectations.calledWith(ctx, name, List.of(a.evaluate(), b.evaluate()));
    }
}
