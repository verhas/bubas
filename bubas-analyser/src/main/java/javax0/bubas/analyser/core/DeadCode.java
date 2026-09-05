package javax0.bubas.analyser.core;

import javax0.bubas.api.BubasException;
import javax0.bubas.lexer.LogicalLine;

import java.math.MathContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Rejects what constant evaluation makes visible.
 * <p>
 * A branch that cannot be taken, a loop body that cannot run, a loop that cannot end: each is
 * something the author meant differently from what they wrote. Nothing is deleted — a compiler that
 * quietly removed the branch would be hiding the mistake it just found — so every one is an error,
 * and the diagnostic says which edit fixes it.
 * <p>
 * It is flow-sensitive, which is most of what it does. A literal condition is the easy case and
 * almost nobody writes one; what people write is a variable set two lines earlier, and
 * {@code n = 5} followed by {@code IF n > 10} is decided before the program runs just as surely.
 * The analysis therefore carries what is known about each variable to each point, and asks whether
 * the condition <em>there</em> has an answer. A variable is not required to be constant for the
 * whole program — only to be settled where it is read.
 * <p>
 * What it knows about a variable comes from the command that wrote it, and only when that command
 * says so with {@link javax0.bubas.api.BubasAssigns}. Everything else a command touches becomes
 * unknown, including a variable merely passed to it: nothing stops a handler writing a variable it
 * was handed, so the analysis assumes the worst rather than trusting a postcondition the runtime
 * does not enforce.
 * <p>
 * It also refuses a block with nothing in it, which needs none of that machinery and is here
 * because this is where the refusals live.
 * <p>
 * Being wrong in that direction is the only safe way to be wrong. A value believed and not held
 * would reject a correct program; a value forgotten only lets a mistake through.
 */
public final class DeadCode {

    /**
     * How much walking a program's loops may cost before the analysis gives up on all of them.
     * <p>
     * Not a property of the program and not something an author can raise: it exists so a loop the
     * analysis can follow but that runs a hundred million times cannot hang a compilation. When a
     * language gains a budget of its own this is where it will be read from — the accessor is
     * already on {@code CoreContext}.
     */
    private static final long WALKING_BUDGET = 100_000;

    private final MathContext mathContext;

    /**
     * True while this is the walk that refuses things, false while it is following a loop to find
     * out where it ends.
     * <p>
     * The two cannot be one walk. Inside a loop being followed every condition is decided on every
     * pass — {@code IF n = 6} has an answer each time round — but the answer differs between
     * passes, so it is not the dead code the rejections are about. Rejecting is therefore done
     * once, conservatively, with everything the body writes forgotten; following is done
     * separately and refuses nothing.
     */
    private boolean rejecting = true;

    private long budget = WALKING_BUDGET;

    private DeadCode(MathContext mathContext) {
        this.mathContext = mathContext;
    }

    /** Thrown to give up on following a loop. Never leaves a {@code walk}. */
    private static final class Abandon extends RuntimeException {
    }

    public static void check(CoreProgram program, MathContext mathContext) {
        // Parameters come from outside and are never known; every other slot starts unassigned,
        // and definite assignment has already refused any program that reads one too early.
        new DeadCode(mathContext).statements(program.body(), Map.of());
    }

    /** @return what is known once the block has run */
    private Map<Integer, Object> statements(List<CoreStatement> body, Map<Integer, Object> known) {
        var state = known;
        for (final var statement : body) {
            state = statement(statement, state);
        }
        return state;
    }

    private Map<Integer, Object> statement(CoreStatement statement, Map<Integer, Object> known) {
        return switch (statement) {
            case CoreStatement.Branch branch -> branch(branch, known);
            case CoreStatement.Loop loop -> loop(loop, known);
            case CoreStatement.Count count -> count(count, known);
            case CoreStatement.Invoke invoke -> invoke(invoke, known);
            case CoreStatement.Procedure procedure -> {
                for (final var argument : procedure.arguments()) {
                    value(argument, known, procedure.line());
                }
                yield known;
            }
            // Abrupt: nothing after them on this path, so what they leave behind is never read.
            case CoreStatement.Break ignored -> known;
            case CoreStatement.Return result -> {
                if (result.value() != null) {
                    value(result.value(), known, result.line());
                }
                yield known;
            }
        };
    }

    private Map<Integer, Object> branch(CoreStatement.Branch branch, Map<Integer, Object> known) {
        final var exits = new ArrayList<Map<Integer, Object>>();
        for (final var arm : branch.arms()) {
            // A condition cannot change a variable — a function may not reach the store — so every
            // arm is tested in the state the branch was entered with.
            final var decided = value(arm.condition(), known, arm.line());
            if (rejecting) {
                if (decided instanceof Boolean always) {
                    throw error(arm.line(), always
                            ? "this condition is always TRUE, so nothing after this arm can run; "
                            + "delete the IF and keep its body"
                            : "this condition is always FALSE, so this arm cannot run; delete it");
                }
                empty(arm.body(), arm.line(), "this arm is empty, so the test above it decides "
                        + "nothing; delete the arm, or write what belongs in it");
                exits.add(statements(arm.body(), known));
                continue;
            }
            // Following a loop: a decided arm is not a fault, it is which way this pass went.
            if (Boolean.TRUE.equals(decided)) {
                return statements(arm.body(), known);
            }
            if (!Boolean.FALSE.equals(decided)) {
                exits.add(statements(arm.body(), known));
            }
        }
        if (rejecting && branch.otherwise() != null) {
            empty(branch.otherwise(), branch.line(), "this ELSE is empty, so it says only that "
                    + "the author stopped; delete it, or write what belongs in it");
        }
        exits.add(branch.otherwise() == null ? known : statements(branch.otherwise(), known));
        return merge(exits);
    }

    private Map<Integer, Object> loop(CoreStatement.Loop loop, Map<Integer, Object> known) {
        // The body may run any number of times, so nothing it writes survives as knowledge — not
        // into the body, not into the condition, not past the loop.
        final var inside = forget(known, writes(loop.body()));
        if (rejecting) {
            if (value(loop.condition(), inside, loop.line()) instanceof Boolean always) {
                if (always) {
                    if (!leaves(loop.body(), Set.of())) {
                        throw error(loop.line(), "this loop never ends: its condition is always "
                                + "TRUE and nothing in it exits");
                    }
                } else {
                    throw error(loop.line(), loop.testAtEnd()
                            ? "this condition is always FALSE, so the body runs exactly once; "
                            + "delete the loop and keep its body"
                            : "this condition is always FALSE, so the body cannot run; delete "
                            + "the loop");
                }
            }
            empty(loop.body(), loop.line(), "this loop has an empty body, so every pass does "
                    + "nothing but test the condition again; delete the loop, or write what it "
                    + "is for");
        }
        statements(loop.body(), inside);
        final var walked = walk(loop, known);
        return walked == null ? inside : walked;
    }

    /**
     * Runs the loop, when every value it turns on is one the analysis already holds.
     * <p>
     * This is the difference between knowing that a loop writes {@code n} and knowing what it
     * leaves in it. {@code n = 5} above a loop that adds one until {@code n} reaches seven leaves
     * seven, and the {@code IF n = 7} below is then a question with an answer — which is the whole
     * reason for doing this.
     * <p>
     * It gives up rather than guesses, and says so with {@code null}: a condition it cannot decide,
     * a statement whose effect nothing declared, arithmetic that traps, a body that can
     * {@code EXIT} or {@code RETURN} — an abrupt exit is a path this does not model — or a budget
     * spent. Giving up costs only precision that was never there before.
     *
     * @return where the loop ends, or {@code null} when it could not be followed
     */
    private Map<Integer, Object> walk(CoreStatement.Loop loop, Map<Integer, Object> known) {
        if (leaves(loop.body(), Set.of())) {
            return null;
        }
        final var wasRejecting = rejecting;
        rejecting = false;
        try {
            var state = known;
            while (true) {
                if (!loop.testAtEnd()) {
                    final var decided = value(loop.condition(), state, loop.line());
                    if (!(decided instanceof Boolean flag)) {
                        return null;
                    }
                    if (!flag) {
                        return state;
                    }
                }
                final var before = state;
                state = statements(loop.body(), state);
                spend();
                if (loop.testAtEnd()) {
                    final var decided = value(loop.condition(), state, loop.line());
                    if (!(decided instanceof Boolean flag)) {
                        return null;
                    }
                    if (!flag) {
                        return state;
                    }
                }
                if (state.equals(before)) {
                    // A pass that changed nothing will change nothing next time either, so this
                    // loop does not end — something the rejections could not see, having forgotten
                    // what the body writes. Left unreported: this walk is here to find values, and
                    // a new refusal falling out of it deserves its own decision.
                    return null;
                }
            }
        } catch (Abandon abandon) {
            return null;
        } finally {
            rejecting = wasRejecting;
        }
    }

    private void spend() {
        if (--budget < 0) {
            throw new Abandon();
        }
    }

    /**
     * Bounds and step are evaluated once, on entry, so constant ones decide the whole loop before
     * it starts. A zero step is a runtime error in general; a constant zero is this.
     */
    private Map<Integer, Object> count(CoreStatement.Count count, Map<Integer, Object> known) {
        final var from = integer(value(count.from(), known, count.line()));
        final var to = integer(value(count.to(), known, count.line()));
        final var step = count.step() == null ? Long.valueOf(1)
                : integer(value(count.step(), known, count.line()));
        if (rejecting) {
            if (step != null && step == 0) {
                throw error(count.line(), "a FOR loop with a step of zero would never finish");
            }
            if (from != null && to != null && step != null && (step > 0 ? from > to : from < to)) {
                throw error(count.line(), "this counts from " + from + " to " + to + " by " + step
                        + ", so the body cannot run");
            }
            empty(count.body(), count.line(), "this loop has an empty body, so it counts and does "
                    + "nothing else; delete the loop, or write what it is for");
        }
        final var written = writes(count.body());
        written.add(count.slot());
        final var inside = forget(known, written);
        statements(count.body(), inside);
        final var walked = walk(count, known, from, to, step);
        return walked == null ? inside : walked;
    }

    /**
     * The counting version, and the easier one: the counter is the language's to move and the body
     * may not assign it, so how many passes there are is settled before the first.
     */
    private Map<Integer, Object> walk(CoreStatement.Count count, Map<Integer, Object> known,
                                      Long from, Long to, Long step) {
        if (from == null || to == null || step == null || step == 0
                || leaves(count.body(), Set.of())) {
            return null;
        }
        final var wasRejecting = rejecting;
        rejecting = false;
        try {
            var state = known;
            long counter = from;
            while (step > 0 ? counter <= to : counter >= to) {
                final var entering = new HashMap<>(state);
                entering.put(count.slot(), counter);
                state = statements(count.body(), entering);
                spend();
                counter = Math.addExact(counter, step);
            }
            // What the language promises afterwards: the first value that failed the test.
            final var ended = new HashMap<>(state);
            ended.put(count.slot(), counter);
            return ended;
        } catch (Abandon | ArithmeticException abandon) {
            return null;
        } finally {
            rejecting = wasRejecting;
        }
    }

    private Map<Integer, Object> invoke(CoreStatement.Invoke invoke, Map<Integer, Object> known) {
        for (final var argument : invoke.arguments().values()) {
            if (argument instanceof CoreArgument.Lazy lazy) {
                value(lazy.expression(), known, invoke.line());
            } else if (argument instanceof CoreArgument.Slot slot && slot.index() != null) {
                value(slot.index(), known, invoke.line());
            }
        }
        // One statement may fill several variables, and may say so about some of them and not
        // others. Everything it touches is forgotten first; only what it declared comes back.
        final var declared = new HashMap<String, String>();
        for (final var assigns : invoke.definition().assigns()) {
            declared.put(assigns.target(), assigns.value());
        }
        final var next = new HashMap<>(known);
        for (final var entry : invoke.arguments().entrySet()) {
            if (!(entry.getValue() instanceof CoreArgument.Slot slot) || slot.index() != null) {
                continue;
            }
            next.remove(slot.slot());
            final var from = declared.get(entry.getKey());
            if (from == null || !Constants.tracked(slot.type())) {
                continue;
            }
            final var assigned = assigned(invoke.arguments().get(from), known, invoke.line());
            if (assigned != null) {
                next.put(slot.slot(), assigned);
            }
        }
        return next;
    }

    /** The value a declared source argument carries, or {@code null} when it is not settled. */
    private Object assigned(CoreArgument source, Map<Integer, Object> known, LogicalLine line) {
        return switch (source) {
            case CoreArgument.Lazy lazy -> value(lazy.expression(), known, line);
            case CoreArgument.Constant constant -> constant.value();
            case null, default -> null;
        };
    }

    /**
     * A block with nothing in it is a half-finished edit, not a construct.
     * <p>
     * It is never meaningful: the only reading under which an empty loop body does anything is one
     * where the condition has side effects, and a rule whose behaviour hides in its test is the
     * cleverness this language exists to keep out. The same goes for an arm and for an {@code ELSE}
     * — what they say is that somebody deleted the contents and left the shape.
     */
    private static void empty(List<CoreStatement> block, LogicalLine line, String message) {
        if (block.isEmpty()) {
            throw error(line, message);
        }
    }

    /** Every slot a block might write. Anything handed to a command counts: {@code set} is not guarded. */
    private static Set<Integer> writes(List<CoreStatement> body) {
        final var written = new HashSet<Integer>();
        for (final var statement : body) {
            switch (statement) {
                case CoreStatement.Invoke invoke -> invoke.arguments().values().forEach(argument -> {
                    if (argument instanceof CoreArgument.Slot slot && slot.index() == null) {
                        written.add(slot.slot());
                    }
                });
                case CoreStatement.Branch branch -> {
                    branch.arms().forEach(arm -> written.addAll(writes(arm.body())));
                    if (branch.otherwise() != null) {
                        written.addAll(writes(branch.otherwise()));
                    }
                }
                case CoreStatement.Loop loop -> written.addAll(writes(loop.body()));
                case CoreStatement.Count count -> {
                    written.add(count.slot());
                    written.addAll(writes(count.body()));
                }
                default -> {
                }
            }
        }
        return written;
    }

    /** A value survives a join only if every path arrives with the same one. */
    private static Map<Integer, Object> merge(List<Map<Integer, Object>> states) {
        final var merged = new HashMap<Integer, Object>();
        for (final var candidate : states.getFirst().entrySet()) {
            if (states.stream().allMatch(state ->
                    candidate.getValue().equals(state.get(candidate.getKey())))) {
                merged.put(candidate.getKey(), candidate.getValue());
            }
        }
        return merged;
    }

    private static Map<Integer, Object> forget(Map<Integer, Object> known, Set<Integer> slots) {
        if (slots.isEmpty()) {
            return known;
        }
        final var kept = new HashMap<>(known);
        kept.keySet().removeAll(slots);
        return kept;
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

    private Object value(CoreExpression expression, Map<Integer, Object> known, LogicalLine line) {
        try {
            return Constants.of(expression, known, mathContext);
        } catch (CoreArithmetic.Trap | MemoizedCall.Refusal trap) {
            if (!rejecting) {
                // Following a loop, not judging it: a trap is a reason to stop looking, not to
                // refuse the program. Reporting it would mean trusting this walk to be right.
                throw new Abandon();
            }
            throw new BubasException(trap.getMessage(), line.line(), line.source(), trap);
        }
    }

    private static Long integer(Object value) {
        return value instanceof Long number ? number : null;
    }

    private static BubasException error(LogicalLine line, String message) {
        return new BubasException(message, line.line(), line.source());
    }
}
