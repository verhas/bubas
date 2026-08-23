package javax0.bubas.runtime;

import javax0.bubas.analyser.core.CoreArgument;
import javax0.bubas.analyser.core.CoreExpression;
import javax0.bubas.analyser.core.CoreProgram;
import javax0.bubas.analyser.core.CoreStatement;
import javax0.bubas.api.ArrayIndex;
import javax0.bubas.api.BubasException;
import javax0.bubas.api.BubasType;
import javax0.bubas.api.ExpressionArg;
import javax0.bubas.api.LiteralArg;
import javax0.bubas.api.StatementContext;
import javax0.bubas.api.Value;
import javax0.bubas.api.VariableArg;
import javax0.bubas.lexer.LogicalLine;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Executes a core tree.
 * <p>
 * There is no interpretation of the language here, only of a fixed operation set: lowering already
 * decided that a {@code +} is decimal addition, that an operand needs widening, that a comparison
 * is by value. This walks what it is given. A code generator for another target implements the same
 * operations, which is what keeps the two from drifting.
 * <p>
 * The machine is also the {@link StatementContext} a command's handler receives. One interpreter
 * runs one program at a time on one thread, so there is nothing to share and no reason for a
 * separate object.
 */
final class Machine implements StatementContext {

    private record Key(Class<?> type, String qualifier) {
    }

    private final CoreProgram program;
    private final Object[] slots;
    private final Map<Key, Object> services;
    private final MathContext mathContext;
    private final BiConsumer<String, String> logger;

    /** The statement being executed, so anything raised below can name a line. */
    private LogicalLine current;
    /** The arguments of the command being invoked, which its handler asks for by name. */
    private Map<String, CoreArgument> arguments = Map.of();

    Machine(CoreProgram program, Object[] slots, Map<Class<?>, Map<String, Object>> services,
            MathContext mathContext, BiConsumer<String, String> logger) {
        this.program = program;
        this.slots = slots;
        this.mathContext = mathContext;
        this.logger = logger;
        this.services = new HashMap<>();
        services.forEach((type, byQualifier) -> byQualifier.forEach(
                (qualifier, service) -> this.services.put(new Key(type, qualifier), service)));
    }

    Object run() {
        try {
            execute(program.body());
        } catch (Signal.Returned returned) {
            return returned.value();
        }
        return null;
    }

    // ------------------------------------------------------------------ statements

    private void execute(List<CoreStatement> body) {
        body.forEach(this::execute);
    }

    private void execute(CoreStatement statement) {
        current = statement.line();
        switch (statement) {
            case CoreStatement.Branch branch -> branch(branch);
            case CoreStatement.Loop loop -> loop(loop);
            case CoreStatement.Count count -> count(count);
            case CoreStatement.Break leave -> throw new Signal.Broke(leave.loopId());
            case CoreStatement.Return result -> throw new Signal.Returned(
                    result.value() == null ? null : evaluate(result.value()));
            case CoreStatement.Procedure procedure ->
                    invoke(procedure.signature(), procedure.arguments());
            case CoreStatement.Invoke command -> command(command);
        }
    }

    private void branch(CoreStatement.Branch branch) {
        for (final var arm : branch.arms()) {
            if (truth(arm.condition())) {
                execute(arm.body());
                return;
            }
        }
        if (branch.otherwise() != null) {
            execute(branch.otherwise());
        }
    }

    private void loop(CoreStatement.Loop loop) {
        try {
            if (loop.testAtEnd()) {
                do {
                    execute(loop.body());
                } while (truth(loop.condition()));
            } else {
                while (truth(loop.condition())) {
                    execute(loop.body());
                }
            }
        } catch (Signal.Broke leave) {
            if (leave.loopId() != loop.id()) {
                throw leave;
            }
        }
    }

    /**
     * Bounds and step are evaluated once, on entry. When the loop ends the variable holds the first
     * value that failed the test, which is what makes it usable afterwards.
     */
    private void count(CoreStatement.Count loop) {
        final long from = (Long) evaluate(loop.from());
        final long to = (Long) evaluate(loop.to());
        final long step = loop.step() == null ? 1L : (Long) evaluate(loop.step());
        if (step == 0) {
            throw fail("a FOR loop with a step of zero would never finish");
        }
        slots[loop.slot()] = from;
        try {
            while (step > 0 ? (Long) slots[loop.slot()] <= to : (Long) slots[loop.slot()] >= to) {
                execute(loop.body());
                slots[loop.slot()] = arithmetic(() ->
                        Math.addExact((Long) slots[loop.slot()], step));
            }
        } catch (Signal.Broke leave) {
            if (leave.loopId() != loop.id()) {
                throw leave;
            }
        }
    }

    private void command(CoreStatement.Invoke command) {
        final var previous = arguments;
        arguments = command.arguments();
        try {
            final var implementation = command.definition().implementation();
            final var parameters = new ArrayList<Object>();
            for (final var placeholder : command.definition().pattern().placeholders()) {
                parameters.add(parameter(command.arguments().get(placeholder.name())));
            }
            call(implementation.instance(), implementation.method(), this, parameters);
        } finally {
            arguments = previous;
        }
    }

    private Object parameter(CoreArgument argument) {
        return switch (argument) {
            case CoreArgument.Slot slot -> new SlotArgument(slot);
            case CoreArgument.Lazy lazy -> new LazyArgument(lazy.expression());
            case CoreArgument.Type type -> type.type();
            case CoreArgument.Constant constant -> constant.value();
        };
    }

    // ------------------------------------------------------------------ expressions

    private boolean truth(CoreExpression condition) {
        return (Boolean) evaluate(condition);
    }

    Object evaluate(CoreExpression expression) {
        return switch (expression) {
            case CoreExpression.Constant constant -> constant.value();
            case CoreExpression.Load load -> slots[load.slot()];
            case CoreExpression.Element element -> element(element);
            case CoreExpression.Widen widen -> BigDecimal.valueOf((Long) evaluate(widen.operand()));
            case CoreExpression.Text text -> text(evaluate(text.operand()));
            case CoreExpression.Concat concat ->
                    (String) evaluate(concat.left()) + evaluate(concat.right());
            case CoreExpression.Arithmetic operation -> arithmetic(operation);
            case CoreExpression.Negate negate -> negate(negate);
            case CoreExpression.Compare compare -> compare(compare);
            case CoreExpression.Not not -> !(Boolean) evaluate(not.operand());
            case CoreExpression.Logical logical ->
                    logical.connective() == CoreExpression.Connective.AND
                            ? truth(logical.left()) && truth(logical.right())
                            : truth(logical.left()) || truth(logical.right());
            case CoreExpression.Call call -> invoke(call.signature(), call.arguments());
        };
    }

    private Object element(CoreExpression.Element element) {
        return Array.get(slots[element.slot()],
                bounds(slots[element.slot()], (Long) evaluate(element.index())));
    }

    /** Plain digits, plain decimal notation keeping scale, and TRUE/FALSE as the literals read. */
    private static String text(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal.toPlainString();
        }
        if (value instanceof Boolean flag) {
            return flag ? "TRUE" : "FALSE";
        }
        return String.valueOf(value);
    }

    private Object arithmetic(CoreExpression.Arithmetic operation) {
        final var left = evaluate(operation.left());
        final var right = evaluate(operation.right());
        return operation.kind() == CoreExpression.Numeric.INTEGER
                ? integer(operation.operator(), (Long) left, (Long) right)
                : decimal(operation.operator(), (BigDecimal) left, (BigDecimal) right);
    }

    /** Overflow is an error, never a wraparound; division truncates and MOD takes the dividend's sign. */
    private Object integer(CoreExpression.Operator operator, long left, long right) {
        return switch (operator) {
            case ADD -> arithmetic(() -> Math.addExact(left, right));
            case SUBTRACT -> arithmetic(() -> Math.subtractExact(left, right));
            case MULTIPLY -> arithmetic(() -> Math.multiplyExact(left, right));
            case DIVIDE -> divide(left, right, false);
            case MODULO -> divide(left, right, true);
        };
    }

    private long divide(long left, long right, boolean modulo) {
        if (right == 0) {
            throw fail(modulo ? "MOD by zero" : "division by zero");
        }
        return arithmetic(() -> modulo ? left % right : left / right);
    }

    private Object decimal(CoreExpression.Operator operator, BigDecimal left, BigDecimal right) {
        return switch (operator) {
            case ADD -> left.add(right);
            case SUBTRACT -> left.subtract(right);
            case MULTIPLY -> left.multiply(right);
            case DIVIDE -> {
                if (right.signum() == 0) {
                    throw fail("division by zero");
                }
                yield left.divide(right, mathContext);
            }
            case MODULO -> throw fail("MOD is defined for INTEGER only");
        };
    }

    private Object negate(CoreExpression.Negate negate) {
        final var operand = evaluate(negate.operand());
        return negate.kind() == CoreExpression.Numeric.INTEGER
                ? arithmetic(() -> Math.negateExact((Long) operand))
                : ((BigDecimal) operand).negate();
    }

    private Object compare(CoreExpression.Compare compare) {
        final var left = evaluate(compare.left());
        final var right = evaluate(compare.right());
        if (compare.kind() == CoreExpression.Comparable.BOOLEAN) {
            final boolean equal = left.equals(right);
            return compare.relation() == CoreExpression.Relation.EQUAL ? equal : !equal;
        }
        final int order = switch (compare.kind()) {
            case INTEGER -> Long.compare((Long) left, (Long) right);
            // By value, never by scale: 2.0 and 2.00 are the same number.
            case DECIMAL -> ((BigDecimal) left).compareTo((BigDecimal) right);
            default -> ((String) left).compareTo((String) right);
        };
        return switch (compare.relation()) {
            case EQUAL -> order == 0;
            case NOT_EQUAL -> order != 0;
            case LESS -> order < 0;
            case LESS_OR_EQUAL -> order <= 0;
            case GREATER -> order > 0;
            case GREATER_OR_EQUAL -> order >= 0;
        };
    }

    // ------------------------------------------------------------------ dispatch

    private Object invoke(javax0.bubas.analyser.FunctionSignature signature,
                          List<CoreExpression> given) {
        final var parameters = given.stream().map(this::evaluate).toList();
        return call(signature.implementation().instance(), signature.implementation().method(),
                this, parameters);
    }

    /**
     * Anything the embedder's code throws is wrapped with the line of the statement that called it,
     * so a failure inside a handler still points at the script.
     */
    private Object call(Object instance, java.lang.reflect.Method method,
                        javax0.bubas.api.Context context, List<Object> parameters) {
        final var arguments = new Object[parameters.size() + 1];
        arguments[0] = context;
        for (int i = 0; i < parameters.size(); i++) {
            arguments[i + 1] = parameters.get(i);
        }
        try {
            return method.invoke(instance, arguments);
        } catch (InvocationTargetException e) {
            throw wrap(e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new BubasException(method.getName() + " could not be called", current.line(),
                    current.source(), e);
        }
    }

    private BubasException wrap(Throwable cause) {
        if (cause instanceof BubasException already) {
            return already;
        }
        return new BubasException(cause instanceof Mistake ? cause.getMessage()
                : cause.getClass().getSimpleName() + ": " + cause.getMessage(),
                current.line(), current.source(), cause);
    }

    private long arithmetic(java.util.function.LongSupplier operation) {
        try {
            return operation.getAsLong();
        } catch (ArithmeticException e) {
            throw new BubasException("integer overflow", current.line(), current.source(), e);
        }
    }

    private int bounds(Object array, long index) {
        final int length = Array.getLength(array);
        if (index < 0 || index >= length) {
            throw fail("index " + index + " is outside an array of " + length);
        }
        return (int) index;
    }

    private BubasException fail(String message) {
        return new BubasException(message, current.line(), current.source());
    }

    // ------------------------------------------------------------------ context

    @Override
    public ExpressionArg expression(String name) {
        return (ExpressionArg) parameter(argument(name));
    }

    @Override
    public VariableArg variable(String name) {
        return (VariableArg) parameter(argument(name));
    }

    @Override
    public LiteralArg literal(String name) {
        final var constant = (CoreArgument.Constant) argument(name);
        return () -> new RuntimeValue(constant.type(), constant.value());
    }

    @Override
    public BubasType type(String name) {
        return ((CoreArgument.Type) argument(name)).type();
    }

    private CoreArgument argument(String name) {
        final var argument = arguments.get(name);
        if (argument == null) {
            throw new Mistake("this statement has no placeholder named '" + name + "'");
        }
        return argument;
    }

    @Override
    public <T> T service(Class<T> type) {
        return service(type, "");
    }

    @Override
    public <T> T service(Class<T> type, String qualifier) {
        final var service = services.get(new Key(type, qualifier));
        if (service == null) {
            throw new Mistake("no " + type.getSimpleName() + " service is registered"
                    + (qualifier.isEmpty() ? "" : " under '" + qualifier + "'"));
        }
        return type.cast(service);
    }

    @Override
    public MathContext mathContext() {
        return mathContext;
    }

    @Override
    public void log(String level, String message) {
        logger.accept(level, message);
    }

    @Override
    public void debug(String message) {
        logger.accept("DEBUG", message);
    }

    @Override
    public void error(String message) {
        throw fail(message);
    }

    // ------------------------------------------------------------------ argument views

    private final class SlotArgument implements VariableArg {
        private final CoreArgument.Slot slot;
        private final IndexArgument index;

        private SlotArgument(CoreArgument.Slot slot) {
            this.slot = slot;
            this.index = slot.index() == null ? null : new IndexArgument(slot.index());
        }

        @Override
        public String name() {
            return slot.name();
        }

        @Override
        public BubasType type() {
            return slot.type();
        }

        @Override
        public boolean isFinal() {
            return slot.isFinal();
        }

        @Override
        public boolean isIndexed() {
            return index != null;
        }

        @Override
        public ArrayIndex index() {
            if (index == null) {
                throw new Mistake("'" + slot.name() + "' is not an indexed reference");
            }
            return index;
        }

        @Override
        public Value get() {
            return new RuntimeValue(slot.type(), isIndexed()
                    ? Array.get(slots[slot.slot()], bounds(slots[slot.slot()], index.get()))
                    : slots[slot.slot()]);
        }

        @Override
        public void set(Value value) {
            set(((RuntimeValue) value).raw());
        }

        @Override
        public void set(Object javaValue) {
            if (isIndexed()) {
                Array.set(slots[slot.slot()], bounds(slots[slot.slot()], index.get()), javaValue);
            } else {
                slots[slot.slot()] = javaValue;
            }
        }
    }

    /** At most one evaluation, because the index selects which location is read or written. */
    private final class IndexArgument implements ArrayIndex {
        private final CoreExpression expression;
        private boolean evaluated;
        private long value;

        private IndexArgument(CoreExpression expression) {
            this.expression = expression;
        }

        @Override
        public void evaluate() {
            if (evaluated) {
                throw new Mistake("the index of an indexed reference may be evaluated once only");
            }
            value = (Long) Machine.this.evaluate(expression);
            evaluated = true;
        }

        @Override
        public long get() {
            if (!evaluated) {
                throw new Mistake("the index has not been evaluated yet");
            }
            return value;
        }
    }

    private final class LazyArgument implements ExpressionArg {
        private final CoreExpression expression;

        private LazyArgument(CoreExpression expression) {
            this.expression = expression;
        }

        @Override
        public Value evaluate() {
            return new RuntimeValue(expression.type(), Machine.this.evaluate(expression));
        }

        @Override
        public BubasType staticType() {
            return expression.type();
        }
    }
}
