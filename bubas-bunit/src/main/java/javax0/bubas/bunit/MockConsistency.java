package javax0.bubas.bunit;

import javax0.bubas.analyser.BubasLanguage;
import javax0.bubas.analyser.BubasProgram;
import javax0.bubas.analyser.CommandDefinition;
import javax0.bubas.analyser.FunctionSignature;
import javax0.bubas.analyser.core.CoreArgument;
import javax0.bubas.analyser.core.CoreExpression;
import javax0.bubas.analyser.core.CoreStatement;
import javax0.bubas.analyser.pattern.Placeholder;
import javax0.bubas.analyser.pattern.Postcondition;
import javax0.bubas.analyser.pattern.ResolvedConstraint;
import javax0.bubas.analyser.pattern.StatementPattern;
import javax0.bubas.api.BubasException;
import javax0.bubas.api.BubasType;
import javax0.bubas.api.TypeNames;
import javax0.bubas.lexer.LogicalLine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Checks a compiled test before it runs.
 * <p>
 * The language already checked the test as a program: syntax, types, definite assignment. That is
 * not enough, because a mock declaration is a <em>statement</em>, so what a mock supplies is a
 * flow-sensitive property. A supply inside one arm of an {@code IF} yields a test that passes today
 * and fails when the other branch is taken, for reasons its author never sees.
 * <p>
 * So this walks the shape the flow analyser walks and merges at joins the same way: what is
 * guaranteed is what holds on <em>every</em> path. It is the argument that kept {@code NULL} out of
 * the language, one level up — a value that might not be there turns every use into a question, and
 * the answer is to prove it is there rather than to check at each use.
 * <p>
 * Nothing here knows a statement by name. A statement says what it does through
 * {@link NamesTarget}, {@link DeclaresMock}, {@link SuppliesVariable}, {@link Act} and
 * {@link Expectation}, and this reads those. Replace the vocabulary and this class does not change.
 */
public final class MockConsistency {

    /**
     * What is known at a point in the test.
     *
     * @param mocked   commands and functions mocked on some path to here
     * @param supplied per mocked command, the variables supplied on every path where it was mocked
     * @param acted    the subject has been run on every path to here
     */
    private record State(Set<String> mocked, Map<String, Set<String>> supplied, boolean acted) {

        static State empty() {
            return new State(Set.of(), Map.of(), false);
        }

        State mock(String target) {
            final var next = new LinkedHashSet<>(mocked);
            next.add(target);
            return new State(next, supplied, acted);
        }

        State supply(String target, String variable) {
            final var next = new LinkedHashMap<String, Set<String>>(supplied);
            final var forTarget = new LinkedHashSet<>(next.getOrDefault(target, Set.of()));
            forTarget.add(variable);
            next.put(target, forTarget);
            return new State(mocked, next, acted);
        }

        State act() {
            return new State(mocked, supplied, true);
        }

        /**
         * A command mocked on both paths keeps only what both supplied; one mocked on a single path
         * keeps that path's, since the other path never mocks it and its real handler writes.
         */
        static State merge(State a, State b) {
            final var mocked = new LinkedHashSet<>(a.mocked);
            mocked.addAll(b.mocked);
            final var supplied = new LinkedHashMap<String, Set<String>>();
            for (final var target : mocked) {
                final var one = a.supplied.get(target);
                final var other = b.supplied.get(target);
                if (a.mocked.contains(target) && b.mocked.contains(target)) {
                    final var both = new LinkedHashSet<>(one == null ? Set.<String>of() : one);
                    both.retainAll(other == null ? Set.of() : other);
                    supplied.put(target, both);
                } else {
                    supplied.put(target, one != null ? one : other == null ? Set.of() : other);
                }
            }
            return new State(mocked, supplied, a.acted && b.acted);
        }
    }

    private final BubasLanguage subject;
    private final Set<String> parameters = new LinkedHashSet<>();
    private final Map<String, CommandDefinition> commandsByName = new LinkedHashMap<>();

    private MockConsistency(BubasLanguage subject, BubasProgram program) {
        this.subject = subject;
        subject.commands().forEach(command ->
                commandsByName.put(canonical(command.name()), command));
        program.variables().subList(0, program.parameterCount())
                .forEach(slot -> parameters.add(slot.name()));
    }

    /**
     * @throws BubasException on the first problem found, carrying the line of the statement that
     *                        caused it
     */
    public static void check(BubasProgram test, BubasProgram subject, BubasLanguage language) {
        final var checker = new MockConsistency(language, subject);
        final var end = checker.body(test.core().body(), State.empty());
        if (!end.acted()) {
            throw new BubasException("this test never runs the subject, so it can only be about"
                    + " nothing. One statement has to be the act.", 0, "");
        }
    }

    private State body(List<CoreStatement> statements, State state) {
        var current = state;
        for (final var statement : statements) {
            current = switch (statement) {
                case CoreStatement.Invoke invoke -> invoke(invoke, current);
                case CoreStatement.Branch branch -> branch(branch, current);
                // A pre-tested loop may run no times at all, so nothing inside it is guaranteed.
                case CoreStatement.Loop loop -> loop.testAtEnd()
                        ? body(loop.body(), current)
                        : State.merge(current, body(loop.body(), current));
                case CoreStatement.Count count ->
                        State.merge(current, body(count.body(), current));
                default -> current;
            };
        }
        return current;
    }

    private State branch(CoreStatement.Branch branch, State state) {
        var merged = branch.otherwise() == null ? state : body(branch.otherwise(), state);
        for (final var arm : branch.arms()) {
            merged = State.merge(merged, body(arm.body(), state));
        }
        return merged;
    }

    private State invoke(CoreStatement.Invoke invoke, State state) {
        final var owner = invoke.definition().implementation().owner();
        final var line = invoke.line();
        var current = state;

        if (owner.isAnnotationPresent(Expectation.class) && !current.acted()) {
            throw error(line, "this expects something of a run that has not happened yet."
                    + " The act has to come first, on every path.");
        }

        final var parameter = owner.getAnnotation(NamesParameter.class);
        if (parameter != null) {
            resolveParameter(constant(invoke, parameter.value(), line, "@NamesParameter"), line);
        }

        final var names = owner.getAnnotation(NamesTarget.class);
        String target = null;
        if (names != null) {
            target = constant(invoke, names.value(), line, "@NamesTarget");
            resolveTarget(target, line);
            arguments(invoke, owner, target, line);
            result(invoke, owner, target, line);
        }

        if (owner.isAnnotationPresent(DeclaresMock.class)) {
            current = current.mock(canonical(target));
        }

        for (final var supplies : owner.getAnnotationsByType(SuppliesVariable.class)) {
            final var variable = constant(invoke, supplies.value(), line, "@SuppliesVariable");
            current = supply(current, target, variable, line);
        }

        if (owner.isAnnotationPresent(Act.class)) {
            everythingMockedIsComplete(current, line);
            current = current.act();
        }
        return current;
    }

    /**
     * How many arguments a statement declares, or {@code null} when it does not say.
     * <p>
     * Either it names its argument placeholders, or it names one placeholder holding a call whose
     * own arguments are the count — {@code ARGS(1, 2)}. The second reads the shape of the
     * expression, which is acceptable here because this is static analysis walking a tree, and
     * because it reads the shape rather than the name: a vocabulary whose collector is not called
     * {@code ARGS} works unchanged.
     */
    private static Integer argumentCount(CoreStatement.Invoke invoke, Class<?> owner,
                                         LogicalLine line) {
        final var named = owner.getAnnotation(MatchesArguments.class);
        if (named != null) {
            for (final var placeholder : named.value()) {
                if (!invoke.arguments().containsKey(placeholder)) {
                    throw error(line, "@MatchesArguments names the placeholder '" + placeholder
                            + "', which this pattern does not have: "
                            + invoke.definition().pattern());
                }
            }
            return named.value().length;
        }
        final var counted = owner.getAnnotation(CountsArguments.class);
        if (counted == null) {
            return null;
        }
        final var argument = invoke.arguments().get(counted.value());
        if (argument == null) {
            throw error(line, "@CountsArguments names the placeholder '" + counted.value()
                    + "', which this pattern does not have: " + invoke.definition().pattern());
        }
        // Only a direct call can be counted. A variable holding an argument list is legal BUBAS and
        // simply not countable here, so the count is skipped rather than guessed at.
        return argument instanceof CoreArgument.Lazy(var expression, var ignored)
                && expression instanceof CoreExpression.Call call
                ? call.arguments().size()
                : null;
    }

    /**
     * A mock declared for the wrong number of arguments matches nothing, and reads at run time as a
     * mock that simply never fired. Counting here says which it is.
     */
    private void arguments(CoreStatement.Invoke invoke, Class<?> owner, String target,
                           LogicalLine line) {
        final Integer given = argumentCount(invoke, owner, line);
        if (given == null) {
            return;
        }
        final var signature = subject.function(target).orElse(null);
        if (signature != null) {
            if (!signature.accepts(given)) {
                throw error(line, signature.arityComplaint(given));
            }
            return;
        }
        final var command = commandsByName.get(canonical(target));
        final var wanted = command.pattern().placeholders().size();
        if (given != wanted) {
            throw error(line, "'" + command.name() + "' takes " + wanted + " argument(s) but this"
                    + " names " + given + ": " + command.pattern());
        }
    }

    /**
     * What a mock answers has to be what the function was declared to return. Otherwise the wrong
     * type surfaces somewhere inside the subject, as a complaint about a value whose origin is no
     * longer on screen.
     */
    private void result(CoreStatement.Invoke invoke, Class<?> owner, String target,
                        LogicalLine line) {
        final var declared = owner.getAnnotation(SuppliesResult.class);
        if (declared == null) {
            return;
        }
        final var argument = invoke.arguments().get(declared.value());
        if (argument == null) {
            throw error(line, "@SuppliesResult names the placeholder '" + declared.value()
                    + "', which this pattern does not have: " + invoke.definition().pattern());
        }
        final var signature = subject.function(target).orElse(null);
        if (signature == null) {
            throw error(line, "'" + target + "' is a command, so it returns nothing to supply.");
        }
        final var supplied = typeOf(argument);
        if (supplied != null && !Token.named(signature.returnType(), supplied)
                && !returns(signature).accepts(supplied)) {
            throw error(line, "'" + target + "' returns " + signature.returnType()
                    + ", but this answers with " + supplied + ": " + signature);
        }
    }

    private static BubasType returns(FunctionSignature signature) {
        return signature.returnType();
    }

    private static BubasType typeOf(CoreArgument argument) {
        return switch (argument) {
            case CoreArgument.Lazy lazy -> lazy.expression().type();
            case CoreArgument.Constant constant -> constant.type();
            case CoreArgument.Slot slot -> slot.type();
            default -> null;
        };
    }

    private void resolveParameter(String name, LogicalLine line) {
        if (!parameters.contains(name)) {
            throw error(line, "the subject has no parameter called '" + name + "'."
                    + (parameters.isEmpty()
                    ? " It takes none."
                    : " It takes " + String.join(", ", parameters) + "."));
        }
    }

    private State supply(State state, String target, String variable, LogicalLine line) {
        if (target == null) {
            throw error(line, "this supplies '" + variable + "' but names no command."
                    + " A statement that supplies a variable must carry @NamesTarget too.");
        }
        final var command = commandsByName.get(canonical(target));
        if (command == null) {
            throw error(line, "'" + target + "' is a function, not a command, so it writes no"
                    + " variables. Only a command's pattern can declare one.");
        }
        final var writes = writtenBy(command.pattern());
        if (!writes.contains(variable)) {
            throw error(line, "'" + target + "' has no variable called '" + variable + "'."
                    + (writes.isEmpty()
                    ? " It writes none at all: " + command.pattern()
                    : " It writes " + String.join(", ", writes) + ": " + command.pattern()));
        }
        if (!state.mocked().contains(canonical(target))) {
            throw error(line, "'" + target + "' is not mocked here, so its own handler writes '"
                    + variable + "'. Supplying it would be ignored.");
        }
        return state.supply(canonical(target), variable);
    }

    /** At the act: everything mocked must already supply what its handler will no longer write. */
    private void everythingMockedIsComplete(State state, LogicalLine line) {
        for (final var target : state.mocked()) {
            final var command = commandsByName.get(target);
            if (command == null) {
                continue;
            }
            final var supplied = state.supplied().getOrDefault(target, Set.of());
            for (final var variable : mustBeSupplied(command)) {
                if (!supplied.contains(variable)) {
                    throw error(line, "'" + command.name() + "' is mocked, so its handler will not"
                            + " write '" + variable + "' — and nothing supplies it on every path."
                            + " Add: \"" + command.name() + "\" SETS \"" + variable + "\" TO ...");
                }
            }
        }
    }

    /**
     * The variables a mocked command must be given. An opaque one is not among them: the framework
     * writes a token, because nothing else could go there and the test cannot construct one either.
     * A type that cannot be resolved from the pattern alone counts as needing a supply — the
     * conservative direction, since being asked for a value you did not need is a nuisance and
     * reading an unwritten slot is a bug.
     */
    private Set<String> mustBeSupplied(CommandDefinition command) {
        final var required = new LinkedHashSet<String>();
        for (final var placeholder : command.pattern().placeholders()) {
            if (!writes(placeholder)) {
                continue;
            }
            final var resolved = subject.constraints()
                    .resolve(command.pattern(), placeholder.constraint());
            if (!(resolved instanceof ResolvedConstraint.Type(var type, var ignored)
                    && type instanceof BubasType.Opaque)) {
                required.add(placeholder.name());
            }
        }
        return required;
    }

    private static Set<String> writtenBy(StatementPattern pattern) {
        final var written = new LinkedHashSet<String>();
        pattern.placeholders().stream().filter(MockConsistency::writes)
                .forEach(placeholder -> written.add(placeholder.name()));
        return written;
    }

    /**
     * A placeholder that leaves a value behind. {@code DECLARED} alone does not: it brings a
     * variable into existence holding nothing, which the flow analyser already accounts for.
     */
    private static boolean writes(Placeholder placeholder) {
        return placeholder.postconditions().contains(Postcondition.INITIALIZED)
                || placeholder.postconditions().contains(Postcondition.FINAL);
    }

    private void resolveTarget(String target, LogicalLine line) {
        if (subject.function(target).isEmpty() && !commandsByName.containsKey(canonical(target))) {
            throw error(line, "the subject's language has no function or command called '"
                    + target + "'. A command is named by its skeleton — "
                    + commandsByName.keySet().stream().findFirst()
                    .map(example -> "\"" + commandsByName.get(example).name() + "\", say")
                    .orElse("the pattern with each placeholder written _"));
        }
    }

    /**
     * Reads a name a statement wrote as a literal. It has to be one: the check runs before the test
     * does, so a computed name would not be there to read.
     */
    private static String constant(CoreStatement.Invoke invoke, String placeholder,
                                   LogicalLine line, String annotation) {
        final var argument = invoke.arguments().get(placeholder);
        if (argument == null) {
            throw error(line, annotation + " names the placeholder '" + placeholder
                    + "', which this pattern does not have: " + invoke.definition().pattern());
        }
        if (!(argument instanceof CoreArgument.Constant(var value, var type, var ignored))
                || type != BubasType.STRING) {
            throw error(line, annotation + " reads '" + placeholder + "', so it has to be a"
                    + " {literal/" + TypeNames.STRING + "} placeholder — the check runs before the"
                    + " test, and anything computed is not there to read yet.");
        }
        return (String) value;
    }

    private static BubasException error(LogicalLine line, String message) {
        return new BubasException(message, line.line(), line.source());
    }

    private static String canonical(String name) {
        return name == null ? null : name.toUpperCase(Locale.ROOT);
    }
}
