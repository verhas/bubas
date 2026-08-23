package javax0.bubas.support;

import javax0.bubas.api.ExpressionArg;
import javax0.bubas.api.StatementContext;
import javax0.bubas.api.VariableArg;

/**
 * {@code x = 5} and {@code a[i] = 5}
 * <p>
 * One command serves both, because a {@code var} placeholder absorbs an index if one is there and
 * the static type of an indexed reference is the element type. Assignment has no keyword: BUBAS is
 * not BASIC, and requiring one on the most frequent line in every script buys nothing.
 * <p>
 * An indexed reference needs its index evaluated before the location can be written, and that
 * evaluation may happen only once — which is exactly what this does.
 */
public final class Assign {

    public static final String PATTERN =
            "{mutable:declared > var:name > initialized} = {expression/name:value}";

    public void call(StatementContext ctx, VariableArg name, ExpressionArg value) {
        if (name.isIndexed()) {
            name.index().evaluate();
        }
        name.set(value.evaluate());
    }
}
