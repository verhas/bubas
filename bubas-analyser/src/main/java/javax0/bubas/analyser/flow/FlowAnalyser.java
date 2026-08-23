package javax0.bubas.analyser.flow;

import javax0.bubas.analyser.BubasLanguage;
import javax0.bubas.analyser.expression.Expression;
import javax0.bubas.analyser.pattern.Placeholder;
import javax0.bubas.analyser.pattern.Postcondition;
import javax0.bubas.analyser.pattern.Precondition;
import javax0.bubas.analyser.pattern.ResolvedConstraint;
import javax0.bubas.analyser.statement.Argument;
import javax0.bubas.analyser.statement.Program;
import javax0.bubas.analyser.statement.Statement;
import javax0.bubas.analyser.symbol.Assignment;
import javax0.bubas.analyser.symbol.SymbolTable;
import javax0.bubas.analyser.symbol.Variable;
import javax0.bubas.api.BubasException;
import javax0.bubas.api.BubasType;
import javax0.bubas.lexer.LogicalLine;
import javax0.bubas.lexer.Token;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Walks a program deciding what is known where.
 * <p>
 * It answers questions about <em>names</em>, not about types: whether a variable definitely holds a
 * value, whether a statement can be reached, whether every path returns. Types are a separate pass
 * over the same tree, because nothing here needs one.
 * <p>
 * Declaredness needs no flow analysis, since a declaration may appear only at the top level and so
 * always runs. Only initialization varies by path, and every rule for it falls out of one
 * intersection at each join — see {@link Assignment}.
 */
public final class FlowAnalyser {

    private final Program program;
    private final BubasLanguage language;
    private final SymbolTable symbols;
    /** The loops enclosing the statement being walked, innermost last. */
    private final Deque<Boolean> loops = new ArrayDeque<>();
    /** Loop variables of enclosing FOR loops: a body may not assign one. */
    private final Set<String> loopVariables = new HashSet<>();
    private boolean returnsAValue;

    private FlowAnalyser(Program program, BubasLanguage language) {
        this.program = program;
        this.language = language;
        this.symbols = new SymbolTable(language.vocabulary());
    }

    /**
     * @return the symbol table it built, so a later pass need not rebuild it
     * @throws BubasException on the first problem found
     */
    public static SymbolTable check(Program program, BubasLanguage language) {
        return new FlowAnalyser(program, language).run();
    }

    private SymbolTable run() {
        returnsAValue = program.returns() != null;
        var state = Assignment.start();
        for (final var parameter : program.parameters()) {
            symbols.declare(program.line(), parameter.name(), parameter.type(), true);
            state = state.initialize(parameter.name().text());
        }
        state = block(program.body(), state);
        if (returnsAValue && state.reachable()) {
            throw new BubasException("this program declares RETURNS " + program.returns()
                    + " but can reach its end without returning a value",
                    program.line().line(), program.line().source());
        }
        final var unread = symbols.neverRead();
        if (!unread.isEmpty()) {
            final var first = unread.getFirst();
            throw new BubasException("'" + first.name() + "' is declared but never read",
                    first.declaredAt().line(), program.line().source());
        }
        return symbols;
    }

    // ------------------------------------------------------------------ statements

    private Assignment block(List<Statement> body, Assignment entry) {
        var state = entry;
        for (final var statement : body) {
            if (!state.reachable()) {
                throw error(statement.line(), "this statement cannot be reached");
            }
            state = statement(statement, state);
        }
        return state;
    }

    private Assignment statement(Statement statement, Assignment state) {
        return switch (statement) {
            case Statement.Command command -> command(command, state);
            case Statement.Call call -> {
                call.arguments().forEach(argument -> reads(call.line(), argument, state));
                yield state;
            }
            case Statement.If branch -> ifStatement(branch, state);
            case Statement.Loop loop -> loop(loop, state);
            case Statement.For loop -> forLoop(loop, state);
            case Statement.Exit exit -> exit(exit, state);
            case Statement.Return statementReturn -> returnStatement(statementReturn, state);
        };
    }

    private Assignment ifStatement(Statement.If branch, Assignment entry) {
        final var exits = new ArrayList<Assignment>();
        for (final var arm : branch.branches()) {
            reads(arm.line(), arm.condition(), entry);
            exits.add(block(arm.body(), entry));
        }
        // Without an ELSE the path that skipped every arm is the entry state itself.
        exits.add(branch.otherwise() == null ? entry : block(branch.otherwise(), entry));
        return Assignment.merge(exits);
    }

    private Assignment loop(Statement.Loop loop, Assignment entry) {
        loops.push(false);
        try {
            if (loop.testAtEnd()) {
                // The body always runs, so what it established survives; the condition is read
                // after it, where the body's knowledge holds.
                final var exit = block(loop.body(), entry);
                reads(loop.line(), loop.condition(), exit);
                return exit;
            }
            reads(loop.line(), loop.condition(), entry);
            // The body may never run, so only what held on entry is guaranteed afterwards.
            return Assignment.merge(List.of(entry, block(loop.body(), entry)));
        } finally {
            loops.pop();
        }
    }

    private Assignment forLoop(Statement.For loop, Assignment entry) {
        final var variable = symbols.resolve(loop.line(), loop.variable());
        if (variable.type() != BubasType.INTEGER) {
            throw error(loop.line(), "'" + variable.name() + "' is " + variable.type()
                    + "; a FOR variable counts, so it must be INTEGER");
        }
        if (variable.isFinal()) {
            throw error(loop.line(), "'" + variable.name() + "' is final and cannot be a FOR "
                    + "variable");
        }
        reads(loop.line(), loop.from(), entry);
        reads(loop.line(), loop.to(), entry);
        if (loop.step() != null) {
            reads(loop.line(), loop.step(), entry);
        }
        // Assigned on entry whether or not the body ever runs, so it survives either way.
        final var inside = entry.initialize(variable.name());
        loops.push(true);
        loopVariables.add(variable.name());
        try {
            return Assignment.merge(List.of(inside, block(loop.body(), inside)));
        } finally {
            loops.pop();
            loopVariables.remove(variable.name());
        }
    }

    private Assignment exit(Statement.Exit exit, Assignment state) {
        if (loops.stream().noneMatch(isFor -> isFor == exit.fromFor())) {
            throw error(exit.line(), "EXIT " + (exit.fromFor() ? "FOR" : "DO")
                    + " has no enclosing " + (exit.fromFor() ? "FOR" : "DO") + " loop to leave");
        }
        return Assignment.unreachable();
    }

    private Assignment returnStatement(Statement.Return statement, Assignment state) {
        if (returnsAValue && statement.value() == null) {
            throw error(statement.line(), "this program declares RETURNS " + program.returns()
                    + ", so RETURN needs a value");
        }
        if (!returnsAValue && statement.value() != null) {
            throw error(statement.line(), "this program declares no RETURNS, so RETURN takes no "
                    + "value");
        }
        if (statement.value() != null) {
            reads(statement.line(), statement.value(), state);
        }
        return Assignment.unreachable();
    }

    // ------------------------------------------------------------------ commands

    private Assignment command(Statement.Command command, Assignment entry) {
        var state = entry;
        for (final var placeholder : command.definition().pattern().placeholders()) {
            final var argument = command.arguments().get(placeholder.name());
            state = placeholder.creates()
                    ? declare(command, placeholder, argument, state)
                    : precondition(command, placeholder, argument, state);
        }
        for (final var placeholder : command.definition().pattern().placeholders()) {
            state = postcondition(command, placeholder,
                    command.arguments().get(placeholder.name()), state);
        }
        return state;
    }

    private Assignment declare(Statement.Command command, Placeholder placeholder,
                               Argument argument, Assignment state) {
        final var name = ((Argument.Name) argument).token();
        final boolean isFinal = placeholder.postconditions().contains(Postcondition.FINAL);
        symbols.declare(command.line(), name, declaredType(command, placeholder), isFinal);
        return state;
    }

    /** The type a creating placeholder declares, which its constraint fixes rather than checks. */
    private BubasType declaredType(Statement.Command command, Placeholder placeholder) {
        return type(command, language.constraints()
                .resolve(command.definition().pattern(), placeholder.constraint()));
    }

    private BubasType type(Statement.Command command, ResolvedConstraint constraint) {
        return switch (constraint) {
            case ResolvedConstraint.Type(var declared, var ignored) -> declared;
            case ResolvedConstraint.Array(var element) -> {
                if (element == null) {
                    throw error(command.line(), "this statement declares an array without an "
                            + "element type");
                }
                yield BubasType.arrayOf(type(command, element));
            }
            case ResolvedConstraint.Reference(var target, var ignored) ->
                    command.arguments().get(target) instanceof Argument.TypeName named
                            ? named.type()
                            : throwUndeclarable(command, target);
            default -> throw error(command.line(),
                    "this statement cannot declare a variable: its constraint names no type");
        };
    }

    private BubasType throwUndeclarable(Statement.Command command, String target) {
        throw error(command.line(), "the declared type refers to '" + target
                + "', which is not a type placeholder");
    }

    private Assignment precondition(Statement.Command command, Placeholder placeholder,
                                    Argument argument, Assignment state) {
        if (!(argument instanceof Argument.Reference reference)) {
            if (argument instanceof Argument.Expr expression) {
                reads(command.line(), expression.expression(), state);
            }
            return state;
        }
        final var variable = symbols.resolve(command.line(), reference.token());
        if (reference.index() != null) {
            reads(command.line(), reference.index(), state);
        }
        if (placeholder.preconditions().contains(Precondition.MUTABLE) && variable.isFinal()) {
            throw error(command.line(), "'" + variable.name() + "' is final and cannot be changed");
        }
        if (placeholder.preconditions().contains(Precondition.FINAL) && !variable.isFinal()) {
            throw error(command.line(), "'" + variable.name() + "' is not final, and this statement "
                    + "requires a constant");
        }
        if (loopVariables.contains(variable.name())
                && placeholder.postconditions().contains(Postcondition.INITIALIZED)) {
            throw error(command.line(), "'" + variable.name() + "' is the variable of an enclosing "
                    + "FOR loop and cannot be assigned inside it");
        }
        if (placeholder.preconditions().contains(Precondition.INITIALIZED)) {
            symbols.reference(command.line(), reference.token());
            if (!state.isInitialized(variable.name())) {
                throw error(command.line(), "'" + variable.name()
                        + "' is not definitely assigned here");
            }
        }
        return state;
    }

    private Assignment postcondition(Statement.Command command, Placeholder placeholder,
                                     Argument argument, Assignment state) {
        if (placeholder.postconditions().isEmpty()) {
            return state;
        }
        // An indexed target changes nothing: the array was initialized at its declaration.
        if (argument instanceof Argument.Reference reference && reference.index() != null) {
            return state;
        }
        final var name = argument.token().text();
        return placeholder.postconditions().contains(Postcondition.DECLARED)
                ? state
                : state.initialize(name);
    }

    // ------------------------------------------------------------------ reads

    private void reads(LogicalLine line, Expression expression, Assignment state) {
        switch (expression) {
            case Expression.Constant ignored -> {
            }
            case Expression.Variable variable -> read(line, variable.token(), state);
            case Expression.Indexed indexed -> {
                read(line, indexed.token(), state);
                reads(line, indexed.index(), state);
            }
            case Expression.Call call ->
                    call.arguments().forEach(argument -> reads(line, argument, state));
            case Expression.Unary unary -> reads(line, unary.operand(), state);
            case Expression.Binary binary -> {
                reads(line, binary.left(), state);
                reads(line, binary.right(), state);
            }
        }
    }

    private void read(LogicalLine line, Token name, Assignment state) {
        final var variable = symbols.reference(line, name);
        if (!state.isInitialized(variable.name())) {
            throw error(line, "'" + variable.name() + "' is read before it is assigned (at "
                    + name.line() + ":" + name.column() + ")");
        }
    }

    private static BubasException error(LogicalLine line, String message) {
        return new BubasException(message, line.line(), line.source());
    }
}
