package javax0.bubas.analyser.match;

import javax0.bubas.lexer.TokenType;

/**
 * The set of tokens a pattern element can consume at one position, abstracted away from any actual
 * token. Overlap analysis works over these rather than over real tokens, because it has to reason
 * about lines nobody has written yet.
 */
public sealed interface TokenClass {

    /** Exactly one token, spelled as a pattern literal spells it. */
    record Exact(TokenType type, String text) implements TokenClass {
    }

    /** The open-ended classes. */
    enum Any implements TokenClass {
        /** A word the script is free to use as a variable name. */
        NAME,
        /** A built-in scalar type or a registered opaque type. */
        TYPE_NAME,
        /** A compile-time constant: a number, a string, or TRUE/FALSE. */
        CONSTANT,
        /** The {@code +} or {@code -} that may precede a numeric constant. */
        SIGN,
        /** Any token that can continue an expression. */
        EXPRESSION
    }
}
