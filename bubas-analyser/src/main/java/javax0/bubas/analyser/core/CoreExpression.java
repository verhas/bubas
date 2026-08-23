package javax0.bubas.analyser.core;

import javax0.bubas.analyser.FunctionSignature;
import javax0.bubas.api.BubasType;
import javax0.bubas.lexer.Token;

import java.util.List;

/**
 * An expression with every type-dependent decision already made.
 * <p>
 * This is the contract between the front end and every back end. A {@code Binary("+")} in the AST
 * could mean integer addition, decimal addition or string concatenation, and each back end would
 * have to work that out for itself — three chances to decide differently, in exactly the subtle
 * ways a debugged script would only discover in production. Here the choice is already made, so
 * the interpreter and each code generator implement the same named operations rather than the same
 * specification.
 * <p>
 * Everything implicit in the source is explicit here: widening, text conversion, bounds checks and
 * the {@code MathContext} a decimal division reads. Nothing is inferred twice.
 */
public sealed interface CoreExpression {

    /** The token this came from, for diagnostics and for mapping generated code back to source. */
    Token token();

    BubasType type();

    /** Which numeric family an operation belongs to. The two never share an implementation. */
    enum Numeric {INTEGER, DECIMAL}

    /** Arithmetic. {@code MODULO} exists only for {@link Numeric#INTEGER}. */
    enum Operator {ADD, SUBTRACT, MULTIPLY, DIVIDE, MODULO}

    enum Relation {EQUAL, NOT_EQUAL, LESS, LESS_OR_EQUAL, GREATER, GREATER_OR_EQUAL}

    /** What is being compared, which decides how. Decimals compare by value, never by scale. */
    enum Comparable {INTEGER, DECIMAL, STRING, BOOLEAN}

    enum Connective {AND, OR}

    /** A literal. {@code value} is a {@code Long}, {@code BigDecimal}, {@code String} or {@code Boolean}. */
    record Constant(Object value, BubasType type, Token token) implements CoreExpression {
    }

    /** Reads a variable by slot. No name survives into execution. */
    record Load(int slot, BubasType type, Token token) implements CoreExpression {
    }

    /** Reads one array element. The index is bounds-checked before the read. */
    record Element(int slot, CoreExpression index, BubasType type, Token token)
            implements CoreExpression {
    }

    /** An INTEGER used where a DECIMAL is wanted. Never implicit; always this node. */
    record Widen(CoreExpression operand, Token token) implements CoreExpression {
        @Override
        public BubasType type() {
            return BubasType.DECIMAL;
        }
    }

    /**
     * A value rendered as text for concatenation: plain digits, plain decimal notation preserving
     * scale, and {@code TRUE}/{@code FALSE} as the literals are written.
     */
    record Text(CoreExpression operand, Token token) implements CoreExpression {
        @Override
        public BubasType type() {
            return BubasType.STRING;
        }
    }

    /**
     * Integer arithmetic traps on overflow and on division by zero; division truncates toward zero
     * and {@code MODULO} takes the dividend's sign. Decimal {@code ADD}, {@code SUBTRACT} and
     * {@code MULTIPLY} are exact, while {@code DIVIDE} reads the interpreter's {@code MathContext}
     * — which is why it can never be folded.
     */
    record Arithmetic(Numeric kind, Operator operator, CoreExpression left, CoreExpression right,
                      Token token) implements CoreExpression {
        @Override
        public BubasType type() {
            return kind == Numeric.INTEGER ? BubasType.INTEGER : BubasType.DECIMAL;
        }
    }

    record Negate(Numeric kind, CoreExpression operand, Token token) implements CoreExpression {
        @Override
        public BubasType type() {
            return kind == Numeric.INTEGER ? BubasType.INTEGER : BubasType.DECIMAL;
        }
    }

    /** Both operands are already STRING; anything else was wrapped in {@link Text} by lowering. */
    record Concat(CoreExpression left, CoreExpression right, Token token) implements CoreExpression {
        @Override
        public BubasType type() {
            return BubasType.STRING;
        }
    }

    record Compare(Comparable kind, Relation relation, CoreExpression left, CoreExpression right,
                   Token token) implements CoreExpression {
        @Override
        public BubasType type() {
            return BubasType.BOOLEAN;
        }
    }

    /** Short-circuiting, and pinned as such: the right side is evaluated only if it decides. */
    record Logical(Connective connective, CoreExpression left, CoreExpression right, Token token)
            implements CoreExpression {
        @Override
        public BubasType type() {
            return BubasType.BOOLEAN;
        }
    }

    record Not(CoreExpression operand, Token token) implements CoreExpression {
        @Override
        public BubasType type() {
            return BubasType.BOOLEAN;
        }
    }

    /** Arguments are evaluated left to right, before the call. */
    record Call(FunctionSignature signature, List<CoreExpression> arguments, Token token)
            implements CoreExpression {
        @Override
        public BubasType type() {
            return signature.returnType();
        }
    }
}
