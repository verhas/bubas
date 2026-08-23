package javax0.bubas.analyser.expression;

import javax0.bubas.analyser.FunctionSignature;
import javax0.bubas.lexer.Token;

import java.util.List;

/**
 * An expression tree.
 * <p>
 * Every node is anchored at a {@link Token} — the operator, the name, the literal — so a diagnostic
 * about any part of an expression can point at the exact place it was written, not merely at the
 * line.
 * <p>
 * A {@link Call} carries the resolved {@link FunctionSignature} rather than a name to look up.
 * Nothing is resolved by name at run time, which is what lets generated Java call the
 * implementation directly instead of dispatching through a registry.
 */
public sealed interface Expression {

    /** The token this node is anchored at. */
    Token token();

    /** A literal: the value is already parsed — {@code Long}, {@code BigDecimal}, {@code String}
     * or {@code Boolean}. */
    record Constant(Token token, Object value) implements Expression {
    }

    /** A plain variable reference. */
    record Variable(Token token) implements Expression {
    }

    /** {@code a[i]}. The index is an ordinary expression. */
    record Indexed(Token token, Expression index) implements Expression {
    }

    /** A call to a registered function, already resolved. */
    record Call(Token token, FunctionSignature signature,
                List<Expression> arguments) implements Expression {
    }

    /** {@code NOT x}, {@code -x}, {@code +x}. */
    record Unary(Token token, Expression operand) implements Expression {
    }

    /** Every binary operator, all left-associative. */
    record Binary(Token token, Expression left, Expression right) implements Expression {
    }
}
