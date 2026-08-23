package javax0.bubas.analyser.statement;

import javax0.bubas.analyser.expression.Expression;
import javax0.bubas.api.BubasType;
import javax0.bubas.lexer.Token;

/**
 * What a command's placeholder captured, after parsing.
 * <p>
 * This is the matcher's {@code Binding} with its token spans turned into trees: an expression
 * binding becomes an {@link Expression}, an index becomes one too, and a constant's sign is folded
 * into its value. The raw tokens do not survive, because nothing downstream wants them.
 */
public sealed interface Argument {

    Token token();

    /** A bare name, from an {@code identifier} placeholder. */
    record Name(Token token) implements Argument {
    }

    /**
     * A reference to storage, from a {@code var} placeholder.
     *
     * @param index {@code null} when the reference is not indexed. It is parsed but never
     *              evaluated here: a command decides whether to evaluate it, at most once.
     */
    record Reference(Token token, Expression index) implements Argument {
    }

    /** An expression, unevaluated. */
    record Expr(Expression expression) implements Argument {
        @Override
        public Token token() {
            return expression.token();
        }
    }

    /**
     * A compile-time constant, with any sign already applied — the lexer does not produce signed
     * literals, so {@code -50.50} arrives here as two tokens and leaves as one value.
     */
    record Constant(Token token, Object value) implements Argument {
    }

    /** A type designator, already resolved. */
    record TypeName(Token token, BubasType type) implements Argument {
    }
}
