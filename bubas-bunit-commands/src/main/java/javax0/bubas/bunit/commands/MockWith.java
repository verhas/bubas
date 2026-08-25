package javax0.bubas.bunit.commands;

import javax0.bubas.bunit.SuppliesResult;
import javax0.bubas.bunit.MatchesArguments;
import javax0.bubas.bunit.DeclaresMock;
import javax0.bubas.bunit.NamesTarget;
import javax0.bubas.api.ExpressionArg;
import javax0.bubas.api.StatementContext;

import java.util.List;

/** {@code "LOAD_ORDER" WITH 42 RETURNS "o1"} */
@NamesTarget("name")
@DeclaresMock
@MatchesArguments("a")
@SuppliesResult("value")
public final class MockWith {

    public static final String PATTERN =
            "{literal/STRING:name} WITH {expression:a} RETURNS {expression:value}";

    public void call(StatementContext ctx, String name, ExpressionArg a, ExpressionArg value) {
        Mock.recorder(ctx).mockFunction(name, List.of(a.evaluate()), value.evaluate());
    }
}
