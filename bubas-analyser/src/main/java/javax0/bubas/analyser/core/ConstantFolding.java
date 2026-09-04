package javax0.bubas.analyser.core;

import javax0.bubas.api.BubasException;
import javax0.bubas.api.BubasType;
import javax0.bubas.lexer.LogicalLine;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Replaces every expression whose value is fixed at compile time with that value.
 * <p>
 * The rewriting is not the point; {@link DeadCode} is. Evaluating constants is what makes a branch
 * that cannot be taken, a loop body that cannot run and arithmetic that cannot succeed visible to
 * an analysis, and all three are mistakes rather than behaviours to preserve.
 * <p>
 * Not a function of the tree alone: folding {@code 100.00 / 3.0} needs the same {@code MathContext}
 * the interpreter would read, so a folded constant belongs to the program and the language
 * together. Division is the only operation this applies to.
 * <p>
 * A constant that traps — overflow, division by zero — is a compile error where it is written,
 * whether or not control could ever reach it.
 */
public final class ConstantFolding {

    private final MathContext mathContext;
    private LogicalLine current;

    private ConstantFolding(MathContext mathContext) {
        this.mathContext = mathContext;
    }

    public static CoreProgram fold(CoreProgram program, MathContext mathContext) {
        final var folding = new ConstantFolding(mathContext);
        return new CoreProgram(program.name(), program.slots(), program.parameters(),
                program.returns(), folding.statements(program.body()));
    }

    private List<CoreStatement> statements(List<CoreStatement> body) {
        final var folded = new ArrayList<CoreStatement>(body.size());
        for (final var statement : body) {
            folded.add(statement(statement));
        }
        return List.copyOf(folded);
    }

    private CoreStatement statement(CoreStatement statement) {
        current = statement.line();
        return switch (statement) {
            case CoreStatement.Branch branch -> {
                final var arms = new ArrayList<CoreStatement.Arm>(branch.arms().size());
                for (final var arm : branch.arms()) {
                    current = arm.line();
                    final var condition = expression(arm.condition());
                    arms.add(new CoreStatement.Arm(condition, statements(arm.body()), arm.line()));
                }
                yield new CoreStatement.Branch(List.copyOf(arms),
                        branch.otherwise() == null ? null : statements(branch.otherwise()),
                        branch.line());
            }
            case CoreStatement.Loop loop -> {
                final var condition = expression(loop.condition());
                yield new CoreStatement.Loop(loop.id(), condition, loop.testAtEnd(),
                        statements(loop.body()), loop.line());
            }
            case CoreStatement.Count count -> {
                final var from = expression(count.from());
                final var to = expression(count.to());
                final var step = count.step() == null ? null : expression(count.step());
                current = count.line();
                yield new CoreStatement.Count(count.id(), count.slot(), from, to, step,
                        statements(count.body()), count.line());
            }
            case CoreStatement.Return result -> new CoreStatement.Return(
                    result.value() == null ? null : expression(result.value()), result.line());
            case CoreStatement.Procedure procedure -> new CoreStatement.Procedure(
                    procedure.signature(), expressions(procedure.arguments()), procedure.line());
            case CoreStatement.Invoke invoke -> {
                final var arguments = new LinkedHashMap<String, CoreArgument>();
                for (final var entry : invoke.arguments().entrySet()) {
                    arguments.put(entry.getKey(), argument(entry.getValue()));
                }
                yield new CoreStatement.Invoke(invoke.definition(),
                        Map.copyOf(arguments), invoke.line());
            }
            case CoreStatement.Break leave -> leave;
        };
    }

    private CoreArgument argument(CoreArgument argument) {
        return switch (argument) {
            case CoreArgument.Slot slot -> slot.index() == null ? slot
                    : new CoreArgument.Slot(slot.slot(), expression(slot.index()), slot.type(),
                    slot.isFinal(), slot.name(), slot.token());
            case CoreArgument.Lazy lazy ->
                    new CoreArgument.Lazy(expression(lazy.expression()), lazy.token());
            case CoreArgument.Constant constant -> constant;
            case CoreArgument.Type type -> type;
        };
    }

    private List<CoreExpression> expressions(List<CoreExpression> given) {
        final var folded = new ArrayList<CoreExpression>(given.size());
        for (final var expression : given) {
            folded.add(expression(expression));
        }
        return List.copyOf(folded);
    }

    /** Bottom-up: operands are folded first, so an operation only ever asks whether they are constant. */
    private CoreExpression expression(CoreExpression expression) {
        return switch (expression) {
            case CoreExpression.Constant constant -> constant;
            case CoreExpression.Load load -> load;
            case CoreExpression.Element element -> new CoreExpression.Element(element.slot(),
                    expression(element.index()), element.type(), element.token());
            case CoreExpression.Call call -> {
                final var arguments = expressions(call.arguments());
                final var values = new ArrayList<>();
                var known = StaticCall.foldable(call.signature());
                for (final var argument : arguments) {
                    final var value = value(argument);
                    known &= value != null;
                    values.add(value);
                }
                yield known
                        ? trapping(() -> constant(
                        StaticCall.of(call.signature(), values, mathContext),
                        call.signature().returnType(), call.token()))
                        : new CoreExpression.Call(call.signature(), arguments, call.token());
            }

            case CoreExpression.Widen widen -> {
                final var operand = expression(widen.operand());
                yield value(operand) instanceof Long integer
                        ? constant(BigDecimal.valueOf(integer), BubasType.DECIMAL, widen.token())
                        : new CoreExpression.Widen(operand, widen.token());
            }
            case CoreExpression.Text text -> {
                final var operand = expression(text.operand());
                final var value = value(operand);
                yield value == null
                        ? new CoreExpression.Text(operand, text.token())
                        : constant(CoreArithmetic.text(value), BubasType.STRING, text.token());
            }
            case CoreExpression.Concat concat -> {
                final var left = expression(concat.left());
                final var right = expression(concat.right());
                final var l = value(left);
                final var r = value(right);
                yield l == null || r == null
                        ? new CoreExpression.Concat(left, right, concat.token())
                        : constant((String) l + (String) r, BubasType.STRING, concat.token());
            }
            case CoreExpression.Not not -> {
                final var operand = expression(not.operand());
                yield value(operand) instanceof Boolean flag
                        ? constant(!flag, BubasType.BOOLEAN, not.token())
                        : new CoreExpression.Not(operand, not.token());
            }
            case CoreExpression.Negate negate -> {
                final var operand = expression(negate.operand());
                final var value = value(operand);
                if (value == null) {
                    yield new CoreExpression.Negate(negate.kind(), operand, negate.token());
                }
                yield trapping(() -> negate.kind() == CoreExpression.Numeric.INTEGER
                        ? constant(CoreArithmetic.negate((Long) value), BubasType.INTEGER,
                        negate.token())
                        : constant(((BigDecimal) value).negate(), BubasType.DECIMAL,
                        negate.token()));
            }
            case CoreExpression.Arithmetic operation -> {
                final var left = expression(operation.left());
                final var right = expression(operation.right());
                final var l = value(left);
                final var r = value(right);
                if (l == null || r == null) {
                    yield new CoreExpression.Arithmetic(operation.kind(), operation.operator(),
                            left, right, operation.token());
                }
                yield trapping(() -> operation.kind() == CoreExpression.Numeric.INTEGER
                        ? constant(CoreArithmetic.integer(operation.operator(), (Long) l, (Long) r),
                        BubasType.INTEGER, operation.token())
                        : constant(CoreArithmetic.decimal(operation.operator(), (BigDecimal) l,
                        (BigDecimal) r, mathContext), BubasType.DECIMAL, operation.token()));
            }
            case CoreExpression.Compare comparison -> {
                final var left = expression(comparison.left());
                final var right = expression(comparison.right());
                final var l = value(left);
                final var r = value(right);
                yield l == null || r == null
                        ? new CoreExpression.Compare(comparison.kind(), comparison.relation(),
                        left, right, comparison.token())
                        : constant(CoreArithmetic.compare(comparison.kind(),
                        comparison.relation(), l, r), BubasType.BOOLEAN, comparison.token());
            }
            case CoreExpression.Logical logical -> {
                final var left = expression(logical.left());
                final var right = expression(logical.right());
                final var l = value(left);
                final var r = value(right);
                if (l == null || r == null) {
                    yield new CoreExpression.Logical(logical.connective(), left, right,
                            logical.token());
                }
                final boolean both = logical.connective() == CoreExpression.Connective.AND
                        ? (Boolean) l && (Boolean) r
                        : (Boolean) l || (Boolean) r;
                yield constant(both, BubasType.BOOLEAN, logical.token());
            }
        };
    }

    /** The value of an already-folded expression, or {@code null} when it is not constant. */
    private static Object value(CoreExpression expression) {
        return expression instanceof CoreExpression.Constant constant ? constant.value() : null;
    }

    private static CoreExpression constant(Object value, BubasType type,
                                           javax0.bubas.lexer.Token token) {
        return new CoreExpression.Constant(value, type, token);
    }

    /**
     * A constant that cannot be computed is a mistake in the source, not a failure to optimise, so
     * it is reported here rather than left for a run that may never reach it.
     */
    private CoreExpression trapping(java.util.function.Supplier<CoreExpression> fold) {
        try {
            return fold.get();
        } catch (CoreArithmetic.Trap trap) {
            throw new BubasException(trap.getMessage(), current.line(), current.source(), trap);
        } catch (StaticCall.Refusal refusal) {
            throw new BubasException(refusal.getMessage(), current.line(), current.source(), refusal);
        }
    }
}
