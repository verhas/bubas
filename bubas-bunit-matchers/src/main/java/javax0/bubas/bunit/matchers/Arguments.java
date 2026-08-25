package javax0.bubas.bunit.matchers;

import javax0.bubas.api.Value;

import java.util.List;

/**
 * The arguments of one call, as a value a statement can be handed.
 * <p>
 * A statement pattern cannot absorb an argument list: an expression stops at a comma, so a
 * placeholder takes one argument and a pattern per arity is the only alternative — a ceiling rather
 * than a design. Collecting the arguments into a value removes the ceiling, and because the value
 * is an opaque type the <em>type checker</em> enforces the form: the only way to produce one is to
 * call the function that builds it.
 * <p>
 * The alternative was a marker function whose call a handler inspects for shape. It cannot be done
 * — {@code ExpressionArg} offers only evaluation — and making it possible would mean exposing the
 * syntax tree to embedder code, and keeping a function that must never be called.
 */
public record Arguments(List<Value> values) {

    public Arguments(List<Value> values) {
        this.values = List.copyOf(values);
    }
}
