package javax0.bubas.analyser.match;

import javax0.bubas.analyser.pattern.Placeholder;
import javax0.bubas.lexer.Token;

import java.util.List;

/** What a placeholder captured from a line. Each shape carries exactly what its kind admits. */
public sealed interface Binding {

    Placeholder placeholder();

    /** A bare name, from an {@code identifier} placeholder. */
    record Name(Placeholder placeholder, Token token) implements Binding {
    }

    /**
     * A reference to storage, from a {@code var} placeholder.
     *
     * @param index the tokens of the index expression, empty when the reference is not indexed.
     *              They are captured, never evaluated: a command decides whether to evaluate them.
     */
    record Reference(Placeholder placeholder, Token name, List<Token> index) implements Binding {
    }

    /** The tokens of an expression, unevaluated. */
    record Expression(Placeholder placeholder, List<Token> tokens) implements Binding {
    }

    /**
     * A compile-time constant.
     *
     * @param sign the {@code +} or {@code -} token that preceded a number, or {@code null}. The
     *             lexer does not produce signed literals, so the sign is reassembled here.
     */
    record Constant(Placeholder placeholder, Token sign, Token token) implements Binding {
    }

    /** A type designator. */
    record TypeName(Placeholder placeholder, Token token) implements Binding {
    }
}
