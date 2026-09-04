package javax0.bubas.analyser.core;

import javax0.bubas.api.BubasException;
import javax0.bubas.lexer.LogicalLine;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Rejects what {@link ConstantFolding} makes visible.
 * <p>
 * A branch that cannot be taken, a loop body that cannot run, a loop that cannot end: each is
 * something the author meant differently from what they wrote. Nothing is deleted — a compiler that
 * quietly removed the branch would be hiding the mistake it just found — so every one of these is
 * an error, and the diagnostic says which edit fixes it.
 * <p>
 * This runs on the folded tree, which is why a condition reduced to a constant by arithmetic is
 * caught alongside one written as {@code TRUE}.
 * <p>
 * Because a constant condition is rejected, no arm is ever known-unreachable <em>because of</em>
 * its condition, and the definite-assignment analysis needs no constant reasoning of its own.
 */
public final class DeadCode {

    private DeadCode() {
    }

    public static void check(CoreProgram program) {
        statements(program.body());
    }

    private static void statements(List<CoreStatement> body) {
        for (final var statement : body) {
            statement(statement);
        }
    }

    private static void statement(CoreStatement statement) {
        switch (statement) {
            case CoreStatement.Branch branch -> {
                for (final var arm : branch.arms()) {
                    if (constant(arm.condition()) instanceof Boolean always) {
                        throw error(branch.line(), always
                                ? "this condition is always TRUE, so nothing after this arm can "
                                + "run; delete the IF and keep its body"
                                : "this condition is always FALSE, so this arm cannot run; "
                                + "delete it");
                    }
                    statements(arm.body());
                }
                if (branch.otherwise() != null) {
                    statements(branch.otherwise());
                }
            }
            case CoreStatement.Loop loop -> {
                if (constant(loop.condition()) instanceof Boolean always) {
                    if (always) {
                        if (!leaves(loop.body(), Set.of())) {
                            throw error(loop.line(), "this loop never ends: its condition is "
                                    + "always TRUE and nothing in it exits");
                        }
                    } else {
                        throw error(loop.line(), loop.testAtEnd()
                                ? "this condition is always FALSE, so the body runs exactly once; "
                                + "delete the loop and keep its body"
                                : "this condition is always FALSE, so the body cannot run; "
                                + "delete the loop");
                    }
                }
                statements(loop.body());
            }
            case CoreStatement.Count count -> {
                count(count);
                statements(count.body());
            }
            case CoreStatement.Break ignored -> {
            }
            case CoreStatement.Return ignored -> {
            }
            case CoreStatement.Invoke ignored -> {
            }
            case CoreStatement.Procedure ignored -> {
            }
        }
    }

    /**
     * Bounds and step are evaluated once, on entry, so constant ones decide the whole loop before
     * it starts. A zero step is a runtime error in general; a constant zero is this.
     */
    private static void count(CoreStatement.Count count) {
        final var step = count.step() == null ? Long.valueOf(1) : integer(count.step());
        if (step != null && step == 0) {
            throw error(count.line(), "a FOR loop with a step of zero would never finish");
        }
        final var from = integer(count.from());
        final var to = integer(count.to());
        if (from == null || to == null || step == null) {
            return;
        }
        if (step > 0 ? from > to : from < to) {
            throw error(count.line(), "this counts from " + from + " to " + to + " by " + step
                    + ", so the body cannot run");
        }
    }

    /**
     * Whether control can leave the loop whose body this is.
     * <p>
     * An {@code EXIT} naming the loop itself is the obvious way, but not the only one: an
     * {@code EXIT} naming a loop further out unwinds through this one, and a {@code RETURN} leaves
     * the program. Only an {@code EXIT} belonging to a loop nested <em>inside</em> the body fails to
     * help, which is what {@code inner} tracks — lowering resolved every {@code EXIT} to an
     * identity, so this is a lookup rather than a search for a matching kind.
     */
    private static boolean leaves(List<CoreStatement> body, Set<Integer> inner) {
        for (final var statement : body) {
            final var found = switch (statement) {
                case CoreStatement.Break leave -> !inner.contains(leave.loopId());
                case CoreStatement.Return ignored -> true;
                case CoreStatement.Branch branch -> leaves(branch.arms().stream()
                        .flatMap(arm -> arm.body().stream()).toList(), inner)
                        || (branch.otherwise() != null && leaves(branch.otherwise(), inner));
                case CoreStatement.Loop loop -> leaves(loop.body(), within(inner, loop.id()));
                case CoreStatement.Count count -> leaves(count.body(), within(inner, count.id()));
                default -> false;
            };
            if (found) {
                return true;
            }
        }
        return false;
    }

    private static Set<Integer> within(Set<Integer> inner, int id) {
        final var deeper = new HashSet<>(inner);
        deeper.add(id);
        return deeper;
    }

    private static Object constant(CoreExpression expression) {
        return expression instanceof CoreExpression.Constant constant ? constant.value() : null;
    }

    private static Long integer(CoreExpression expression) {
        return constant(expression) instanceof Long value ? value : null;
    }

    private static BubasException error(LogicalLine line, String message) {
        return new BubasException(message, line.line(), line.source());
    }
}
