package javax0.bubas.api;

/** An expression a command may evaluate — when it likes, as often as it likes, or never. */
public interface ExpressionArg {

    Value evaluate();

    /** The type the analyser proved the expression has, known without evaluating it. */
    BubasType staticType();
}
