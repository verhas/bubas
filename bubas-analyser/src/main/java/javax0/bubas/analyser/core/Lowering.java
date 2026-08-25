package javax0.bubas.analyser.core;

import javax0.bubas.analyser.BubasLanguage;
import javax0.bubas.analyser.FunctionSignature;
import javax0.bubas.analyser.expression.Expression;
import javax0.bubas.analyser.pattern.Placeholder;
import javax0.bubas.analyser.pattern.ResolvedConstraint;
import javax0.bubas.analyser.statement.Argument;
import javax0.bubas.analyser.statement.Program;
import javax0.bubas.analyser.statement.Statement;
import javax0.bubas.analyser.symbol.SymbolTable;
import javax0.bubas.analyser.symbol.Variable;
import javax0.bubas.api.BubasException;
import javax0.bubas.api.BubasType;
import javax0.bubas.lexer.LogicalLine;
import javax0.bubas.lexer.Token;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Types every expression and emits the {@link CoreProgram} every back end consumes.
 * <p>
 * Typing and lowering are one pass on purpose. Knowing that {@code a + b} is decimal addition is
 * exactly what choosing {@code Arithmetic(DECIMAL, ADD, …)} requires, so a separate type checker
 * would compute that knowledge, throw it away, and leave lowering to derive it again — two places
 * for one set of rules, which is the divergence the core tree exists to prevent.
 * <p>
 * It does more than check. Where a constraint or a parameter wants a {@code DECIMAL} and the
 * expression is an {@code INTEGER}, this inserts the {@link CoreExpression.Widen}; where a
 * concatenation needs text, it inserts the {@link CoreExpression.Text}. Nothing downstream infers
 * a conversion.
 */
public final class Lowering {

    private record Enclosing(int id, boolean isFor) {
    }

    private final Program program;
    private final BubasLanguage language;
    private final SymbolTable symbols;
    private final Map<String, Integer> slots = new LinkedHashMap<>();
    private final Deque<Enclosing> loops = new ArrayDeque<>();
    private int nextLoopId;

    private Lowering(Program program, BubasLanguage language, SymbolTable symbols) {
        this.program = program;
        this.language = language;
        this.symbols = symbols;
        final var declared = symbols.declared();
        for (int i = 0; i < declared.size(); i++) {
            slots.put(declared.get(i).name(), i);
        }
    }

    public static CoreProgram lower(Program program, BubasLanguage language, SymbolTable symbols) {
        return new Lowering(program, language, symbols).run();
    }

    private CoreProgram run() {
        final var declared = symbols.declared();
        final var coreSlots = declared.stream()
                .map(v -> new CoreProgram.Slot(v.name(), v.type(), v.isFinal()))
                .toList();
        return new CoreProgram(program.name().text(), coreSlots, program.parameters().size(),
                program.returns(), block(program.body()));
    }

    // ------------------------------------------------------------------ statements

    private List<CoreStatement> block(List<Statement> body) {
        return body.stream().map(this::statement).toList();
    }

    private CoreStatement statement(Statement statement) {
        return switch (statement) {
            case Statement.If branch -> branch(branch);
            case Statement.Loop loop -> loop(loop);
            case Statement.For loop -> counting(loop);
            case Statement.Exit exit -> new CoreStatement.Break(target(exit), exit.line());
            case Statement.Return result -> result(result);
            case Statement.Call call -> new CoreStatement.Procedure(call.signature(),
                    call(call.line(), call.signature(), call.arguments()), call.line());
            case Statement.Command command -> command(command);
        };
    }

    private CoreStatement branch(Statement.If branch) {
        final var arms = branch.branches().stream()
                .map(arm -> new CoreStatement.Arm(
                        condition(arm.line(), arm.condition(), "IF"), block(arm.body())))
                .toList();
        return new CoreStatement.Branch(arms,
                branch.otherwise() == null ? null : block(branch.otherwise()), branch.line());
    }

    /** {@code UNTIL} becomes a {@code WHILE} over a negated condition; no back end sees the flag. */
    private CoreStatement loop(Statement.Loop loop) {
        var condition = condition(loop.line(), loop.condition(), "DO");
        if (loop.until()) {
            condition = new CoreExpression.Not(condition, loop.condition().token());
        }
        final int id = nextLoopId++;
        loops.push(new Enclosing(id, false));
        try {
            return new CoreStatement.Loop(id, condition, loop.testAtEnd(), block(loop.body()),
                    loop.line());
        } finally {
            loops.pop();
        }
    }

    private CoreStatement counting(Statement.For loop) {
        final var from = counter(loop.line(), loop.from(), "the initial value");
        final var to = counter(loop.line(), loop.to(), "the bound");
        final var step = loop.step() == null ? null : counter(loop.line(), loop.step(), "STEP");
        final int id = nextLoopId++;
        loops.push(new Enclosing(id, true));
        try {
            return new CoreStatement.Count(id, slot(loop.line(), loop.variable()), from, to, step,
                    block(loop.body()), loop.line());
        } finally {
            loops.pop();
        }
    }

    /** {@code EXIT FOR} leaves the innermost enclosing loop of that kind, resolved here by id. */
    private int target(Statement.Exit exit) {
        return loops.stream()
                .filter(enclosing -> enclosing.isFor() == exit.fromFor())
                .findFirst()
                .orElseThrow(() -> error(exit.line(), "EXIT " + (exit.fromFor() ? "FOR" : "DO")
                        + " has no enclosing loop of that kind"))
                .id();
    }

    private CoreStatement result(Statement.Return result) {
        if (result.value() == null) {
            return new CoreStatement.Return(null, result.line());
        }
        final var value = expression(result.line(), result.value());
        if (!program.returns().accepts(value.type())) {
            throw error(result.line(), "this program declares RETURNS " + program.returns()
                    + ", so it cannot return " + value.type());
        }
        return new CoreStatement.Return(coerce(value, program.returns()), result.line());
    }

    // ------------------------------------------------------------------ commands

    private CoreStatement command(Statement.Command command) {
        final var arguments = new LinkedHashMap<String, CoreArgument>();
        for (final var placeholder : command.definition().pattern().placeholders()) {
            arguments.put(placeholder.name(),
                    argument(command, placeholder, command.arguments().get(placeholder.name())));
        }
        return new CoreStatement.Invoke(command.definition(), Map.copyOf(arguments), command.line());
    }

    private CoreArgument argument(Statement.Command command, Placeholder placeholder,
                                  Argument argument) {
        final var required = placeholder.creates() || placeholder.constraint() == null ? null
                : language.constraints()
                .resolve(command.definition().pattern(), placeholder.constraint());
        return switch (argument) {
            case Argument.Name name -> {
                final var variable = symbols.resolve(command.line(), name.token());
                yield new CoreArgument.Slot(slot(command.line(), name.token()), null,
                        variable.type(), variable.isFinal(), variable.name(), name.token());
            }
            case Argument.Reference reference -> reference(command, reference, required);
            case Argument.Expr expression -> lazy(command, placeholder, expression, required);
            case Argument.Constant constant -> {
                final var type = constantType(constant.value());
                check(command, placeholder, required, type);
                yield new CoreArgument.Constant(constant.value(), type, constant.token());
            }
            case Argument.TypeName type -> new CoreArgument.Type(type.type(), type.token());
        };
    }

    private CoreArgument reference(Statement.Command command, Argument.Reference reference,
                                   ResolvedConstraint required) {
        final var variable = symbols.resolve(command.line(), reference.token());
        CoreExpression index = null;
        var type = variable.type();
        if (reference.index() != null) {
            index = index(command.line(), reference.index());
            type = element(command.line(), reference.token(), variable.type());
        }
        checkType(command, reference.token().text(), required, type);
        return new CoreArgument.Slot(slot(command.line(), reference.token()), index, type,
                variable.isFinal(), variable.name(), reference.token());
    }

    private CoreArgument lazy(Statement.Command command, Placeholder placeholder,
                              Argument.Expr argument, ResolvedConstraint required) {
        var value = expression(command.line(), argument.expression());
        check(command, placeholder, required, value.type());
        // Only a constraint naming one type can say what to widen to. NUMBER names a choice and
        // an array constraint names a shape, so both leave the expression as it is.
        if (required instanceof ResolvedConstraint.Type
                || required instanceof ResolvedConstraint.Reference
                || required instanceof ResolvedConstraint.Element) {
            value = coerce(value, requiredType(command, required));
        }
        return new CoreArgument.Lazy(value, argument.token());
    }

    private void check(Statement.Command command, Placeholder placeholder,
                       ResolvedConstraint required, BubasType actual) {
        checkType(command, placeholder.name(), required, actual);
    }

    private void checkType(Statement.Command command, String what, ResolvedConstraint required,
                           BubasType actual) {
        if (required == null) {
            return;
        }
        switch (required) {
            case ResolvedConstraint.Numeric ignored -> {
                if (!number(actual)) {
                    throw mismatch(command, what, "a number", actual);
                }
            }
            case ResolvedConstraint.Array(var element) -> {
                if (!(actual instanceof BubasType.ArrayOf(var actualElement))) {
                    throw mismatch(command, what, "an array", actual);
                }
                if (element != null
                        && !requiredType(command, element).equals(actualElement)) {
                    throw mismatch(command, what,
                            "an array of " + requiredType(command, element), actual);
                }
            }
            case ResolvedConstraint.Type(var wanted, var exact) ->
                    accept(command, what, wanted, actual, exact);
            case ResolvedConstraint.Reference(var ignored, var exact) ->
                    accept(command, what, requiredType(command, required), actual, exact);
            case ResolvedConstraint.Element ignored ->
                    accept(command, what, requiredType(command, required), actual, false);
        }
    }

    private void accept(Statement.Command command, String what, BubasType wanted, BubasType actual,
                        boolean exact) {
        if (exact ? !wanted.equals(actual) : !wanted.accepts(actual)) {
            throw mismatch(command, what, (exact ? "exactly " : "") + wanted, actual);
        }
    }

    /** The type a constraint demands, once its references are followed. */
    private BubasType requiredType(Statement.Command command, ResolvedConstraint constraint) {
        return switch (constraint) {
            case ResolvedConstraint.Type(var type, var ignored) -> type;
            case ResolvedConstraint.Reference(var target, var ignored) ->
                    argumentType(command, target);
            case ResolvedConstraint.Element(var target) ->
                    argumentType(command, target) instanceof BubasType.ArrayOf(var element)
                            ? element
                            : throwNoElement(command, target);
            case ResolvedConstraint.Array(var element) -> element == null
                    ? throwNoElement(command, "this constraint")
                    : BubasType.arrayOf(requiredType(command, element));
            case ResolvedConstraint.Numeric ignored -> throwNoElement(command, "NUMBER");
        };
    }

    private BubasType argumentType(Statement.Command command, String name) {
        final var argument = command.arguments().get(name);
        return switch (argument) {
            case Argument.Name value -> symbols.resolve(command.line(), value.token()).type();
            case Argument.Reference value -> {
                final var declared = symbols.resolve(command.line(), value.token()).type();
                yield value.index() == null ? declared
                        : element(command.line(), value.token(), declared);
            }
            case Argument.Expr value -> expression(command.line(), value.expression()).type();
            case Argument.Constant value -> constantType(value.value());
            case Argument.TypeName value -> value.type();
            case null -> throw error(command.line(), "this pattern has no placeholder named '"
                    + name + "'");
        };
    }

    private BubasType throwNoElement(Statement.Command command, String what) {
        throw error(command.line(), what + " names no single type, so nothing can be checked "
                + "against it");
    }

    private BubasException mismatch(Statement.Command command, String what, String expected,
                                    BubasType actual) {
        return error(command.line(), "'" + what + "' must be " + expected + ", but this one is "
                + actual);
    }

    // ------------------------------------------------------------------ expressions

    private CoreExpression expression(LogicalLine line, Expression expression) {
        return switch (expression) {
            case Expression.Constant constant -> new CoreExpression.Constant(constant.value(),
                    constantType(constant.value()), constant.token());
            case Expression.Variable variable -> new CoreExpression.Load(
                    slot(line, variable.token()),
                    symbols.resolve(line, variable.token()).type(), variable.token());
            case Expression.Indexed indexed -> new CoreExpression.Element(
                    slot(line, indexed.token()), index(line, indexed.index()),
                    element(line, indexed.token(),
                            symbols.resolve(line, indexed.token()).type()), indexed.token());
            case Expression.Call call -> new CoreExpression.Call(call.signature(),
                    call(line, call.signature(), call.arguments()), call.token());
            case Expression.Unary unary -> unary(line, unary);
            case Expression.Binary binary -> binary(line, binary);
        };
    }

    private List<CoreExpression> call(LogicalLine line, FunctionSignature signature,
                                      List<Expression> given) {
        final var arguments = new ArrayList<CoreExpression>();
        for (int i = 0; i < given.size(); i++) {
            final var expected = signature.typeOf(i);
            final var actual = expression(line, given.get(i));
            if (!expected.accepts(actual.type())) {
                throw error(line, signature.name() + " takes " + expected + " for '"
                        + signature.nameOf(i) + "', but was given " + actual.type());
            }
            arguments.add(coerce(actual, expected));
        }
        return List.copyOf(arguments);
    }

    private CoreExpression unary(LogicalLine line, Expression.Unary unary) {
        final var operand = expression(line, unary.operand());
        if (unary.token().is("NOT")) {
            if (operand.type() != BubasType.BOOLEAN) {
                throw error(line, "NOT needs a BOOLEAN, not " + operand.type());
            }
            return new CoreExpression.Not(operand, unary.token());
        }
        if (!number(operand.type())) {
            throw error(line, "unary " + unary.token().text() + " needs a number, not "
                    + operand.type());
        }
        // Unary plus is identity, so it leaves no node behind.
        return unary.token().is("+") ? operand
                : new CoreExpression.Negate(numeric(operand.type()), operand, unary.token());
    }

    private CoreExpression binary(LogicalLine line, Expression.Binary binary) {
        final var operator = binary.token().text();
        final var left = expression(line, binary.left());
        final var right = expression(line, binary.right());
        if (binary.token().is("AND") || binary.token().is("OR")) {
            if (left.type() != BubasType.BOOLEAN || right.type() != BubasType.BOOLEAN) {
                throw error(line, operator.toUpperCase(java.util.Locale.ROOT)
                        + " needs BOOLEAN on both sides, not " + left.type() + " and "
                        + right.type());
            }
            return new CoreExpression.Logical(binary.token().is("AND")
                    ? CoreExpression.Connective.AND : CoreExpression.Connective.OR,
                    left, right, binary.token());
        }
        final var relation = relation(operator);
        if (relation != null) {
            return comparison(line, binary.token(), relation, left, right);
        }
        if (binary.token().is("+") && left.type() == BubasType.STRING) {
            return concatenation(line, binary.token(), left, right);
        }
        return arithmetic(line, binary.token(), left, right);
    }

    /**
     * {@code +} coerces only when the left operand is a STRING, so {@code 42 + "x"} is an error and
     * {@code "" + 42 + "x"} is not. The right operand is wrapped in a {@link CoreExpression.Text}
     * unless it is already text, which is where the rendering rules are pinned.
     */
    private CoreExpression concatenation(LogicalLine line, Token token, CoreExpression left,
                                         CoreExpression right) {
        if (right.type() instanceof BubasType.Opaque || right.type() instanceof BubasType.ArrayOf) {
            throw error(line, right.type() + " has no text form and cannot be added to a STRING; "
                    + "the embedder can expose a domain-named function for it");
        }
        return new CoreExpression.Concat(left,
                right.type() == BubasType.STRING ? right : new CoreExpression.Text(right, token),
                token);
    }

    private CoreExpression arithmetic(LogicalLine line, Token token, CoreExpression left,
                                      CoreExpression right) {
        if (right.type() == BubasType.STRING) {
            throw error(line, "a STRING can only be added to a STRING; write \"\" + "
                    + left.type() + " first if a text result is wanted");
        }
        if (!number(left.type()) || !number(right.type())) {
            throw error(line, token.text() + " needs numbers, not " + left.type() + " and "
                    + right.type());
        }
        final var operator = operator(token);
        if (operator == CoreExpression.Operator.MODULO
                && (left.type() != BubasType.INTEGER || right.type() != BubasType.INTEGER)) {
            throw error(line, "MOD is defined for INTEGER only, not " + left.type() + " and "
                    + right.type());
        }
        final var kind = left.type() == BubasType.DECIMAL || right.type() == BubasType.DECIMAL
                ? CoreExpression.Numeric.DECIMAL : CoreExpression.Numeric.INTEGER;
        final var wanted = kind == CoreExpression.Numeric.DECIMAL
                ? BubasType.DECIMAL : BubasType.INTEGER;
        return new CoreExpression.Arithmetic(kind, operator, coerce(left, wanted),
                coerce(right, wanted), token);
    }

    private CoreExpression comparison(LogicalLine line, Token token,
                                      CoreExpression.Relation relation, CoreExpression left,
                                      CoreExpression right) {
        final var l = left.type();
        final var r = right.type();
        if (l instanceof BubasType.Opaque || r instanceof BubasType.Opaque
                || l instanceof BubasType.ArrayOf || r instanceof BubasType.ArrayOf) {
            throw error(line, l + " and " + r + " cannot be compared; an opaque value is a black "
                    + "box, so the embedder decides what comparing two of them means");
        }
        final boolean ordering = relation != CoreExpression.Relation.EQUAL
                && relation != CoreExpression.Relation.NOT_EQUAL;
        if (l == BubasType.BOOLEAN || r == BubasType.BOOLEAN) {
            if (l != r) {
                throw error(line, "cannot compare " + l + " with " + r);
            }
            if (ordering) {
                throw error(line, "BOOLEAN values compare only with = and <>, not " + token.text());
            }
            return new CoreExpression.Compare(CoreExpression.Comparable.BOOLEAN, relation, left,
                    right, token);
        }
        if (l == BubasType.STRING || r == BubasType.STRING) {
            if (l != r) {
                throw error(line, "cannot compare " + l + " with " + r);
            }
            return new CoreExpression.Compare(CoreExpression.Comparable.STRING, relation, left,
                    right, token);
        }
        if (!number(l) || !number(r)) {
            throw error(line, "cannot compare " + l + " with " + r);
        }
        final boolean decimal = l == BubasType.DECIMAL || r == BubasType.DECIMAL;
        final var wanted = decimal ? BubasType.DECIMAL : BubasType.INTEGER;
        return new CoreExpression.Compare(
                decimal ? CoreExpression.Comparable.DECIMAL : CoreExpression.Comparable.INTEGER,
                relation, coerce(left, wanted), coerce(right, wanted), token);
    }

    // ------------------------------------------------------------------ helpers

    private CoreExpression condition(LogicalLine line, Expression source, String what) {
        final var condition = expression(line, source);
        if (condition.type() != BubasType.BOOLEAN) {
            throw error(line, what + " needs a BOOLEAN condition, but this one is "
                    + condition.type());
        }
        return condition;
    }

    private CoreExpression counter(LogicalLine line, Expression source, String what) {
        final var value = expression(line, source);
        if (value.type() != BubasType.INTEGER) {
            throw error(line, "a FOR loop counts, so " + what + " must be INTEGER, not "
                    + value.type());
        }
        return value;
    }

    private CoreExpression index(LogicalLine line, Expression source) {
        final var value = expression(line, source);
        if (value.type() != BubasType.INTEGER) {
            throw error(line, "an array index must be INTEGER, not " + value.type());
        }
        return value;
    }

    private BubasType element(LogicalLine line, Token token, BubasType declared) {
        if (declared instanceof BubasType.ArrayOf(var element)) {
            return element;
        }
        throw error(line, "'" + token.text() + "' is " + declared + " and cannot be indexed");
    }

    /** The only place an implicit conversion becomes a node. Nothing else may widen. */
    private static CoreExpression coerce(CoreExpression value, BubasType wanted) {
        return wanted == BubasType.DECIMAL && value.type() == BubasType.INTEGER
                ? new CoreExpression.Widen(value, value.token())
                : value;
    }

    private int slot(LogicalLine line, Token name) {
        final Variable variable = symbols.resolve(line, name);
        return slots.get(variable.name());
    }

    private static boolean number(BubasType type) {
        return type == BubasType.INTEGER || type == BubasType.DECIMAL;
    }

    private static CoreExpression.Numeric numeric(BubasType type) {
        return type == BubasType.DECIMAL
                ? CoreExpression.Numeric.DECIMAL : CoreExpression.Numeric.INTEGER;
    }

    private static CoreExpression.Operator operator(Token token) {
        if (token.is("+")) {
            return CoreExpression.Operator.ADD;
        }
        if (token.is("-")) {
            return CoreExpression.Operator.SUBTRACT;
        }
        if (token.is("*")) {
            return CoreExpression.Operator.MULTIPLY;
        }
        return token.is("/") ? CoreExpression.Operator.DIVIDE : CoreExpression.Operator.MODULO;
    }

    private static CoreExpression.Relation relation(String operator) {
        return switch (operator) {
            case "=" -> CoreExpression.Relation.EQUAL;
            case "<>" -> CoreExpression.Relation.NOT_EQUAL;
            case "<" -> CoreExpression.Relation.LESS;
            case "<=" -> CoreExpression.Relation.LESS_OR_EQUAL;
            case ">" -> CoreExpression.Relation.GREATER;
            case ">=" -> CoreExpression.Relation.GREATER_OR_EQUAL;
            default -> null;
        };
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
