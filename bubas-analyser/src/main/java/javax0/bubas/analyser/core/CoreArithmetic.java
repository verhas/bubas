package javax0.bubas.analyser.core;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.function.LongSupplier;

/**
 * What every core operation means, in one place.
 * <p>
 * The interpreter executes these and {@link ConstantFolding} evaluates them at compile time. Two
 * implementations would agree on everything except the edge cases and would disagree invisibly: a
 * folded expression and an unfolded one differing exactly where it matters most. So there is one,
 * and both callers reach it.
 * <p>
 * Nothing here knows about lines or programs. An operation with no value throws {@link Trap} and
 * the caller — which does know where it is — turns that into a diagnostic.
 */
public final class CoreArithmetic {

    private CoreArithmetic() {
    }

    /**
     * An operation that cannot produce a value: overflow, division by zero, a quotient with no
     * finite form under an {@code UNLIMITED} rounding policy.
     * <p>
     * Extends {@code ArithmeticException} because that is what the operations underneath throw and
     * what a caller unaware of this class would already be catching.
     */
    public static final class Trap extends ArithmeticException {

        public Trap(String message) {
            super(message);
        }

        public Trap(String message, Throwable cause) {
            super(message);
            initCause(cause);
        }
    }

    /** Overflow is an error, never a wraparound; division truncates and MOD takes the dividend's sign. */
    public static long integer(CoreExpression.Operator operator, long left, long right) {
        return switch (operator) {
            case ADD -> trapping(() -> Math.addExact(left, right));
            case SUBTRACT -> trapping(() -> Math.subtractExact(left, right));
            case MULTIPLY -> trapping(() -> Math.multiplyExact(left, right));
            case DIVIDE -> divide(left, right, false);
            case MODULO -> divide(left, right, true);
        };
    }

    public static long negate(long value) {
        return trapping(() -> Math.negateExact(value));
    }

    /**
     * {@code ADD}, {@code SUBTRACT} and {@code MULTIPLY} are exact and never read the context;
     * {@code DIVIDE} is the only operation whose result depends on it.
     */
    public static BigDecimal decimal(CoreExpression.Operator operator, BigDecimal left,
                                     BigDecimal right, MathContext mathContext) {
        return switch (operator) {
            case ADD -> left.add(right);
            case SUBTRACT -> left.subtract(right);
            case MULTIPLY -> left.multiply(right);
            case DIVIDE -> {
                if (right.signum() == 0) {
                    throw new Trap("division by zero");
                }
                try {
                    yield left.divide(right, mathContext);
                } catch (ArithmeticException e) {
                    throw new Trap("this quotient has no finite decimal form, and the rounding "
                            + "policy is UNLIMITED", e);
                }
            }
            case MODULO -> throw new Trap("MOD is defined for INTEGER only");
        };
    }

    /** Plain digits, plain decimal notation keeping scale, and TRUE/FALSE as the literals read. */
    public static String text(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal.toPlainString();
        }
        if (value instanceof Boolean flag) {
            return flag ? "TRUE" : "FALSE";
        }
        return String.valueOf(value);
    }

    public static boolean compare(CoreExpression.Comparable kind, CoreExpression.Relation relation,
                                  Object left, Object right) {
        if (kind == CoreExpression.Comparable.BOOLEAN) {
            final boolean equal = left.equals(right);
            return relation == CoreExpression.Relation.EQUAL ? equal : !equal;
        }
        final int order = switch (kind) {
            case INTEGER -> Long.compare((Long) left, (Long) right);
            // By value, never by scale: 2.0 and 2.00 are the same number.
            case DECIMAL -> ((BigDecimal) left).compareTo((BigDecimal) right);
            default -> ((String) left).compareTo((String) right);
        };
        return switch (relation) {
            case EQUAL -> order == 0;
            case NOT_EQUAL -> order != 0;
            case LESS -> order < 0;
            case LESS_OR_EQUAL -> order <= 0;
            case GREATER -> order > 0;
            case GREATER_OR_EQUAL -> order >= 0;
        };
    }

    private static long divide(long left, long right, boolean modulo) {
        if (right == 0) {
            throw new Trap(modulo ? "MOD by zero" : "division by zero");
        }
        return trapping(() -> modulo ? left % right : left / right);
    }

    private static long trapping(LongSupplier operation) {
        try {
            return operation.getAsLong();
        } catch (Trap trap) {
            throw trap;
        } catch (ArithmeticException e) {
            throw new Trap("integer overflow", e);
        }
    }
}
