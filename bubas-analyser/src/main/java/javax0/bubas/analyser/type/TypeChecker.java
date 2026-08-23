package javax0.bubas.analyser.type;

import javax0.bubas.analyser.BubasLanguage;
import javax0.bubas.analyser.expression.Expression;
import javax0.bubas.analyser.pattern.Placeholder;
import javax0.bubas.analyser.pattern.ResolvedConstraint;
import javax0.bubas.analyser.statement.Argument;
import javax0.bubas.analyser.statement.Program;
import javax0.bubas.analyser.statement.Statement;
import javax0.bubas.analyser.symbol.SymbolTable;
import javax0.bubas.api.BubasException;
import javax0.bubas.api.BubasType;
import javax0.bubas.lexer.LogicalLine;
import javax0.bubas.lexer.Token;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * Gives every expression a type and rejects the ones that cannot have one.
 * <p>
 * A second walk over the same tree the {@link javax0.bubas.analyser.flow.FlowAnalyser} walked,
 * using the symbol table it built. The two are separate because definite assignment asks about
 * names and this asks about types, and neither needs the other's answer.
 */
public final class TypeChecker {

    private static final Set<String> ORDERING = Set.of("<", ">", "<=", ">=");
    private static final Set<String> EQUALITY = Set.of("=", "<>");

    private final Program program;
    private final BubasLanguage language;
    private final SymbolTable symbols;

    private TypeChecker(Program program, BubasLanguage language, SymbolTable symbols) {
        this.program = program;
        this.language = language;
        this.symbols = symbols;
    }

    public static void check(Program program, BubasLanguage language, SymbolTable symbols) {
        new TypeChecker(program, language, symbols).block(program.body());
    }

    // ------------------------------------------------------------------ statements

    private void block(List<Statement> body) {
        body.forEach(this::statement);
    }

    private void statement(Statement statement) {
        switch (statement) {
            case Statement.If branch -> {
                branch.branches().forEach(arm -> {
                    condition(arm.line(), arm.condition(), "IF");
                    block(arm.body());
                });
                if (branch.otherwise() != null) {
                    block(branch.otherwise());
                }
            }
            case Statement.Loop loop -> {
                condition(loop.line(), loop.condition(), "DO");
                block(loop.body());
            }
            case Statement.For loop -> {
                counter(loop.line(), loop.from(), "the initial value");
                counter(loop.line(), loop.to(), "the bound");
                if (loop.step() != null) {
                    counter(loop.line(), loop.step(), "STEP");
                }
                block(loop.body());
            }
            case Statement.Return statementReturn -> returnValue(statementReturn);
            case Statement.Call call -> arguments(call.line(), call.signature().parameters(),
                    call.arguments(), call.signature().name());
            case Statement.Command command -> command(command);
            case Statement.Exit ignored -> {
            }
        }
    }

    private void condition(LogicalLine line, Expression condition, String what) {
        final var type = typeOf(line, condition);
        if (type != BubasType.BOOLEAN) {
            throw error(line, what + " needs a BOOLEAN condition, but this one is " + type);
        }
    }

    private void counter(LogicalLine line, Expression expression, String what) {
        final var type = typeOf(line, expression);
        if (type != BubasType.INTEGER) {
            throw error(line, "a FOR loop counts, so " + what + " must be INTEGER, not " + type);
        }
    }

    private void returnValue(Statement.Return statement) {
        if (statement.value() == null) {
            return;
        }
        final var type = typeOf(statement.line(), statement.value());
        if (!program.returns().accepts(type)) {
            throw error(statement.line(), "this program declares RETURNS " + program.returns()
                    + ", so it cannot return " + type);
        }
    }

    private void arguments(LogicalLine line, List<javax0.bubas.analyser.FunctionSignature.Parameter>
            parameters, List<Expression> given, String name) {
        for (int i = 0; i < parameters.size(); i++) {
            final var expected = parameters.get(i).type();
            final var actual = typeOf(line, given.get(i));
            if (!expected.accepts(actual)) {
                throw error(line, name + " takes " + expected + " for '" + parameters.get(i).name()
                        + "', but was given " + actual);
            }
        }
    }

    // ------------------------------------------------------------------ commands

    private void command(Statement.Command command) {
        for (final var placeholder : command.definition().pattern().placeholders()) {
            if (placeholder.creates() || placeholder.constraint() == null) {
                continue;
            }
            final var argument = command.arguments().get(placeholder.name());
            final var resolved = language.constraints()
                    .resolve(command.definition().pattern(), placeholder.constraint());
            check(command, placeholder, argument, resolved);
        }
    }

    private void check(Statement.Command command, Placeholder placeholder, Argument argument,
                       ResolvedConstraint constraint) {
        final var actual = typeOf(command, argument);
        switch (constraint) {
            case ResolvedConstraint.Numeric ignored -> {
                if (actual != BubasType.INTEGER && actual != BubasType.DECIMAL) {
                    throw mismatch(command, placeholder, "a number", actual);
                }
            }
            case ResolvedConstraint.Array(var element) -> {
                if (!(actual instanceof BubasType.ArrayOf(var actualElement))) {
                    throw mismatch(command, placeholder, "an array", actual);
                }
                if (element != null && !required(command, element).equals(actualElement)) {
                    throw mismatch(command, placeholder,
                            "an array of " + required(command, element), actual);
                }
            }
            case ResolvedConstraint.Type(var expected, var exact) ->
                    accept(command, placeholder, expected, actual, exact);
            case ResolvedConstraint.Reference(var target, var exact) ->
                    accept(command, placeholder, required(command, constraint), actual, exact);
            case ResolvedConstraint.Element ignored ->
                    accept(command, placeholder, required(command, constraint), actual, false);
        }
    }

    private void accept(Statement.Command command, Placeholder placeholder, BubasType expected,
                        BubasType actual, boolean exact) {
        final boolean ok = exact ? expected.equals(actual) : expected.accepts(actual);
        if (!ok) {
            throw mismatch(command, placeholder, (exact ? "exactly " : "") + expected, actual);
        }
    }

    /** The type a constraint demands, once its references are followed. */
    private BubasType required(Statement.Command command, ResolvedConstraint constraint) {
        return switch (constraint) {
            case ResolvedConstraint.Type(var type, var ignored) -> type;
            case ResolvedConstraint.Reference(var target, var ignored) ->
                    typeOf(command, command.arguments().get(target));
            case ResolvedConstraint.Element(var target) ->
                    typeOf(command, command.arguments().get(target))
                            instanceof BubasType.ArrayOf(var element)
                            ? element
                            : throwNotAnArray(command, target);
            case ResolvedConstraint.Array(var element) -> element == null
                    ? throwNoElementType(command)
                    : BubasType.arrayOf(required(command, element));
            case ResolvedConstraint.Numeric ignored -> throwNoElementType(command);
        };
    }

    /** Reached only if a constraint that names no single type is used where one is required. */
    private BubasType throwNoElementType(Statement.Command command) {
        throw error(command.line(), "this constraint names no single type, so nothing can be "
                + "checked against it");
    }

    private BubasType throwNotAnArray(Statement.Command command, String target) {
        throw error(command.line(), "'" + target + "' is not an array here, so it has no element "
                + "type");
    }

    private BubasType typeOf(Statement.Command command, Argument argument) {
        return switch (argument) {
            case Argument.Name name -> symbols.resolve(command.line(), name.token()).type();
            case Argument.Reference reference -> {
                final var declared = symbols.resolve(command.line(), reference.token()).type();
                if (reference.index() == null) {
                    yield declared;
                }
                index(command.line(), reference.index());
                yield declared instanceof BubasType.ArrayOf(var element) ? element
                        : throwNotIndexable(command.line(), reference.token(), declared);
            }
            case Argument.Expr expression -> typeOf(command.line(), expression.expression());
            case Argument.Constant constant -> constantType(constant.value());
            case Argument.TypeName type -> type.type();
        };
    }

    private BubasException mismatch(Statement.Command command, Placeholder placeholder,
                                    String expected, BubasType actual) {
        return error(command.line(), "'" + placeholder.name() + "' must be " + expected
                + ", but this one is " + actual);
    }

    // ------------------------------------------------------------------ expressions

    private BubasType typeOf(LogicalLine line, Expression expression) {
        return switch (expression) {
            case Expression.Constant constant -> constantType(constant.value());
            case Expression.Variable variable -> symbols.resolve(line, variable.token()).type();
            case Expression.Indexed indexed -> {
                index(line, indexed.index());
                final var declared = symbols.resolve(line, indexed.token()).type();
                yield declared instanceof BubasType.ArrayOf(var element) ? element
                        : throwNotIndexable(line, indexed.token(), declared);
            }
            case Expression.Call call -> {
                arguments(line, call.signature().parameters(), call.arguments(),
                        call.signature().name());
                yield call.signature().returnType();
            }
            case Expression.Unary unary -> unary(line, unary);
            case Expression.Binary binary -> binary(line, binary);
        };
    }

    private void index(LogicalLine line, Expression expression) {
        final var type = typeOf(line, expression);
        if (type != BubasType.INTEGER) {
            throw error(line, "an array index must be INTEGER, not " + type);
        }
    }

    private BubasType throwNotIndexable(LogicalLine line, Token token, BubasType declared) {
        throw error(line, "'" + token.text() + "' is " + declared + " and cannot be indexed");
    }

    private BubasType unary(LogicalLine line, Expression.Unary unary) {
        final var operand = typeOf(line, unary.operand());
        if (unary.token().is("NOT")) {
            if (operand != BubasType.BOOLEAN) {
                throw error(line, "NOT needs a BOOLEAN, not " + operand);
            }
            return BubasType.BOOLEAN;
        }
        if (operand != BubasType.INTEGER && operand != BubasType.DECIMAL) {
            throw error(line, "unary " + unary.token().text() + " needs a number, not " + operand);
        }
        return operand;
    }

    private BubasType binary(LogicalLine line, Expression.Binary binary) {
        final var operator = binary.token().text();
        final var left = typeOf(line, binary.left());
        final var right = typeOf(line, binary.right());
        if (binary.token().is("AND") || binary.token().is("OR")) {
            if (left != BubasType.BOOLEAN || right != BubasType.BOOLEAN) {
                throw error(line, binary.token().text().toUpperCase(java.util.Locale.ROOT)
                        + " needs BOOLEAN on both sides, not " + left + " and " + right);
            }
            return BubasType.BOOLEAN;
        }
        if (ORDERING.contains(operator) || EQUALITY.contains(operator)) {
            return comparison(line, operator, left, right);
        }
        if ("+".equals(operator) && left == BubasType.STRING) {
            return concatenation(line, right);
        }
        return arithmetic(line, binary.token(), left, right);
    }

    /**
     * {@code +} coerces only when the <em>left</em> operand is a STRING. The asymmetry is
     * deliberate: {@code 42 + "x"} is an error and {@code "" + 42 + "x"} is not.
     */
    private BubasType concatenation(LogicalLine line, BubasType right) {
        if (right instanceof BubasType.Opaque || right instanceof BubasType.ArrayOf) {
            throw error(line, "an opaque value has no text form; " + right + " cannot be added to "
                    + "a STRING. The embedder can expose a domain-named function for it");
        }
        return BubasType.STRING;
    }

    private BubasType arithmetic(LogicalLine line, Token operator, BubasType left, BubasType right) {
        if (right == BubasType.STRING) {
            throw error(line, "a STRING can only be added to a STRING; write \"\" + " + left
                    + " first if a text result is wanted");
        }
        final boolean modulo = operator.is("MOD");
        if (!number(left) || !number(right)) {
            throw error(line, operator.text() + " needs numbers, not " + left + " and " + right);
        }
        if (modulo && (left != BubasType.INTEGER || right != BubasType.INTEGER)) {
            throw error(line, "MOD is defined for INTEGER only, not " + left + " and " + right);
        }
        return left == BubasType.DECIMAL || right == BubasType.DECIMAL
                ? BubasType.DECIMAL : BubasType.INTEGER;
    }

    private BubasType comparison(LogicalLine line, String operator, BubasType left, BubasType right) {
        if (left instanceof BubasType.Opaque || right instanceof BubasType.Opaque
                || left instanceof BubasType.ArrayOf || right instanceof BubasType.ArrayOf) {
            throw error(line, left + " and " + right + " cannot be compared; an opaque value is a "
                    + "black box, so the embedder decides what comparing two of them means");
        }
        if (left == BubasType.BOOLEAN || right == BubasType.BOOLEAN) {
            if (left != right) {
                throw error(line, "cannot compare " + left + " with " + right);
            }
            if (ORDERING.contains(operator)) {
                throw error(line, "BOOLEAN values compare only with = and <>, not " + operator);
            }
            return BubasType.BOOLEAN;
        }
        if (left == BubasType.STRING || right == BubasType.STRING) {
            if (left != right) {
                throw error(line, "cannot compare " + left + " with " + right);
            }
            return BubasType.BOOLEAN;
        }
        if (!number(left) || !number(right)) {
            throw error(line, "cannot compare " + left + " with " + right);
        }
        return BubasType.BOOLEAN;
    }

    private static boolean number(BubasType type) {
        return type == BubasType.INTEGER || type == BubasType.DECIMAL;
    }

    private static BubasType constantType(Object value) {
        if (value instanceof Long) {
            return BubasType.INTEGER;
        }
        if (value instanceof BigDecimal) {
            return BubasType.DECIMAL;
        }
        return value instanceof Boolean ? BubasType.BOOLEAN : BubasType.STRING;
    }

    private static BubasException error(LogicalLine line, String message) {
        return new BubasException(message, line.line(), line.source());
    }
}
