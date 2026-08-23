package javax0.bubas.api;

/**
 * What a command implementation can reach: everything a function can, plus its pattern's
 * placeholders.
 * <p>
 * A command receives expressions <em>unevaluated</em> and decides whether, and how often, to
 * evaluate them — the opposite of a function, whose arguments arrive evaluated. That is what lets a
 * custom statement express control flow rather than only side effects.
 */
public interface StatementContext extends Context {

    ExpressionArg expression(String name);

    VariableArg variable(String name);

    LiteralArg literal(String name);

    BubasType type(String name);
}
