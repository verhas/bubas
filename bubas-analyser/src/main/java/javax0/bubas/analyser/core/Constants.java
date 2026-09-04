package javax0.bubas.analyser.core;

import javax0.bubas.api.BubasType;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Map;

/**
 * The value of an expression at one point in a program, given what is known about the variables
 * there, or {@code null} when it is not decided.
 * <p>
 * Where {@link ConstantFolding} rewrites what is fixed for the whole program, this answers the
 * narrower question {@link DeadCode} asks: {@code n} is not a constant — it is assigned again on
 * the next line — but on <em>this</em> line it holds 5, and a condition reading it is decided
 * before the program runs.
 * <p>
 * Nothing here interprets an operation itself; {@link CoreArithmetic} does, so an expression
 * evaluated at compile time cannot disagree with the same expression evaluated at run time.
 */
final class Constants {

    private Constants() {
    }

    /**
     * A function is folded only when it said it may be, and then only if every argument is known.
     * Arguments are evaluated whatever the answer, so a trap in one is reported either way — the
     * call would have evaluated them all before running.
     */
    private static Object call(CoreExpression.Call call, Map<Integer, Object> known,
                               MathContext mathContext) {
        final var arguments = new java.util.ArrayList<>();
        var settled = true;
        for (final var argument : call.arguments()) {
            final var value = of(argument, known, mathContext);
            settled &= value != null;
            arguments.add(value);
        }
        return settled && MemoizedCall.foldable(call.signature())
                ? MemoizedCall.of(call.signature(), arguments, mathContext)
                : null;
    }

    /** Only a scalar has a value worth carrying. An array is a store, and an opaque type is a black box. */
    static boolean tracked(BubasType type) {
        return type == BubasType.INTEGER || type == BubasType.DECIMAL
                || type == BubasType.STRING || type == BubasType.BOOLEAN;
    }

    /**
     * @param known slot to value, holding only what is certain at this point
     * @throws CoreArithmetic.Trap when the expression is decided and cannot be computed
     */
    static Object of(CoreExpression expression, Map<Integer, Object> known,
                     MathContext mathContext) {
        return switch (expression) {
            case CoreExpression.Constant constant -> constant.value();
            case CoreExpression.Load load -> known.get(load.slot());
            // An element is a store the analysis does not follow.
            case CoreExpression.Element ignored -> null;
            // A call answers anything at all, unless it has declared that it does not.
            case CoreExpression.Call call -> call(call, known, mathContext);

            case CoreExpression.Widen widen -> of(widen.operand(), known, mathContext)
                    instanceof Long integer ? BigDecimal.valueOf(integer) : null;
            case CoreExpression.Text text -> {
                final var operand = of(text.operand(), known, mathContext);
                yield operand == null ? null : CoreArithmetic.text(operand);
            }
            case CoreExpression.Not not -> of(not.operand(), known, mathContext)
                    instanceof Boolean flag ? !flag : null;
            case CoreExpression.Negate negate -> {
                final var operand = of(negate.operand(), known, mathContext);
                yield operand == null ? null
                        : negate.kind() == CoreExpression.Numeric.INTEGER
                        ? CoreArithmetic.negate((Long) operand)
                        : ((BigDecimal) operand).negate();
            }
            case CoreExpression.Concat concat -> {
                final var left = of(concat.left(), known, mathContext);
                final var right = of(concat.right(), known, mathContext);
                yield left == null || right == null ? null : (String) left + (String) right;
            }
            case CoreExpression.Arithmetic operation -> {
                final var left = of(operation.left(), known, mathContext);
                final var right = of(operation.right(), known, mathContext);
                yield left == null || right == null ? null
                        : operation.kind() == CoreExpression.Numeric.INTEGER
                        ? CoreArithmetic.integer(operation.operator(), (Long) left, (Long) right)
                        : CoreArithmetic.decimal(operation.operator(), (BigDecimal) left,
                        (BigDecimal) right, mathContext);
            }
            case CoreExpression.Compare comparison -> {
                final var left = of(comparison.left(), known, mathContext);
                final var right = of(comparison.right(), known, mathContext);
                yield left == null || right == null ? null
                        : CoreArithmetic.compare(comparison.kind(), comparison.relation(),
                        left, right);
            }
            // Short-circuiting, so one side can decide it: FALSE AND anything is FALSE, whatever
            // the other side is, and a rule that missed this would be one a rewrite could evade.
            case CoreExpression.Logical logical -> {
                final var left = of(logical.left(), known, mathContext);
                final var decides = logical.connective() == CoreExpression.Connective.AND
                        ? Boolean.FALSE : Boolean.TRUE;
                if (decides.equals(left)) {
                    yield decides;
                }
                final var right = of(logical.right(), known, mathContext);
                if (decides.equals(right)) {
                    yield decides;
                }
                yield left == null || right == null ? null : !decides;
            }
        };
    }
}
