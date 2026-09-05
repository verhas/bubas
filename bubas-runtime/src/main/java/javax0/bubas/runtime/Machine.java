package javax0.bubas.runtime;

import javax0.bubas.analyser.core.CoreArgument;
import javax0.bubas.analyser.core.CoreArithmetic;
import javax0.bubas.analyser.core.CoreExpression;
import javax0.bubas.analyser.core.CoreProgram;
import javax0.bubas.analyser.core.CoreStatement;
import javax0.bubas.api.*;
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
    private final javax0.bubas.api.BubasCallInterceptor interceptor;
    private final MathContext mathContext;
    private final Limits limits;
    private final BiConsumer<String, String> logger;
    /** Statements executed and loop passes taken, together. Never reset; a run happens once. */
    private long steps;

    /**
     * The statement being executed, so anything raised below can name a line.
     */
    private LogicalLine current;
    /**
     * The arguments of the command being invoked, which its handler asks for by name.
     */
    private Map<String, CoreArgument> arguments = Map.of();

    Machine(CoreProgram program, Object[] slots, Map<Class<?>, Map<String, Object>> services,
            MathContext mathContext, Limits limits, BiConsumer<String, String> logger,
            javax0.bubas.api.BubasCallInterceptor interceptor) {
        this.program = program;
        this.slots = slots;
        this.mathContext = mathContext;
        this.limits = limits;
        this.logger = logger;
        this.interceptor = interceptor;
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
        step();
        switch (statement) {
            case CoreStatement.Branch branch -> branch(branch);
            case CoreStatement.Loop loop -> loop(loop);
            case CoreStatement.Count count -> count(count);
            case CoreStatement.Break leave -> throw new Signal.Broke(leave.loopId());
            case CoreStatement.Return result -> throw new Signal.Returned(
                    result.value() == null ? null : evaluate(result.value()));
            case CoreStatement.Procedure procedure -> invoke(procedure.signature(), procedure.arguments());
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
            // A pass costs a step of its own: testing the condition is work the program does,
            // and on a FOR so is moving the counter.
            if (loop.testAtEnd()) {
                do {
                    execute(loop.body());
                    step();
                } while (truth(loop.condition()));
            } else {
                step();
                while (truth(loop.condition())) {
                    execute(loop.body());
                    step();
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
                step();
                execute(loop.body());
                slots[loop.slot()] = trapping(() -> CoreArithmetic.integer(
                        CoreExpression.Operator.ADD, (Long) slots[loop.slot()], step));
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
            final var declared = implementation.method().getParameterTypes();
            final var placeholders = command.definition().pattern().placeholders();
            final var source = command.definition().pattern().source();
            if (interceptor != null && interceptor.interceptsCommand(source)) {
                final var byName = new LinkedHashMap<String, Object>();
                for (int i = 0; i < placeholders.size(); i++) {
                    byName.put(placeholders.get(i).name(),
                            parameter(command.arguments().get(placeholders.get(i).name()),
                                    declared[i + 1]));
                }
                interceptor.onCommand(source, this, byName);
                return;
            }
            final var parameters = new ArrayList<>();
            for (int i = 0; i < placeholders.size(); i++) {
                parameters.add(parameter(command.arguments().get(placeholders.get(i).name()),
                        declared[i + 1]));
            }
            call(command.definition().name(), implementation.instance(),
                    implementation.method(), this, parameters);
        } finally {
            arguments = previous;
        }
    }

    /**
     * A constrained literal arrives as the Java type its constraint fixes; an unconstrained one has
     * no single type to arrive as, so it arrives as a {@link Value}. Which of the two is decided by
     * what the handler declared, checked at seal.
     */
    private Object parameter(CoreArgument argument, Class<?> declared) {
        return switch (argument) {
            case CoreArgument.Slot slot -> new SlotArgument(slot);
            case CoreArgument.Lazy lazy -> new LazyArgument(lazy.expression());
            case CoreArgument.Type type -> type.type();
            case CoreArgument.Constant constant -> declared == Value.class
                    ? new RuntimeValue(constant.type(), constant.value())
                    : constant.value();
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
            case CoreExpression.Text text -> CoreArithmetic.text(evaluate(text.operand()));
            case CoreExpression.Concat concat -> (String) evaluate(concat.left()) + evaluate(concat.right());
            case CoreExpression.Arithmetic operation -> arithmetic(operation);
            case CoreExpression.Negate negate -> negate(negate);
            case CoreExpression.Compare compare -> compare(compare);
            case CoreExpression.Not not -> !(Boolean) evaluate(not.operand());
            case CoreExpression.Logical logical -> logical.connective() == CoreExpression.Connective.AND
                    ? truth(logical.left()) && truth(logical.right())
                    : truth(logical.left()) || truth(logical.right());
            case CoreExpression.Call call -> invoke(call.signature(), call.arguments());
        };
    }

    private Object element(CoreExpression.Element element) {
        return Array.get(slots[element.slot()],
                bounds(slots[element.slot()], (Long) evaluate(element.index())));
    }

    private Object arithmetic(CoreExpression.Arithmetic operation) {
        final var left = evaluate(operation.left());
        final var right = evaluate(operation.right());
        return trapping(() -> operation.kind() == CoreExpression.Numeric.INTEGER
                ? CoreArithmetic.integer(operation.operator(), (Long) left, (Long) right)
                : CoreArithmetic.decimal(operation.operator(), (BigDecimal) left,
                (BigDecimal) right, mathContext));
    }

    private Object negate(CoreExpression.Negate negate) {
        final var operand = evaluate(negate.operand());
        return trapping(() -> negate.kind() == CoreExpression.Numeric.INTEGER
                ? CoreArithmetic.negate((Long) operand)
                : ((BigDecimal) operand).negate());
    }

    /** The operation says what went wrong; this says where. */
    private Object trapping(java.util.function.Supplier<Object> operation) {
        try {
            return operation.get();
        } catch (CoreArithmetic.Trap trap) {
            throw new BubasException(trap.getMessage(), current.line(), current.source(), trap);
        }
    }

    private Object compare(CoreExpression.Compare compare) {
        return CoreArithmetic.compare(compare.kind(), compare.relation(),
                evaluate(compare.left()), evaluate(compare.right()));
    }

    // ------------------------------------------------------------------ dispatch

    private Object invoke(javax0.bubas.analyser.FunctionSignature signature,
                          List<CoreExpression> given) {
        if (interceptor != null && interceptor.interceptsFunction(signature.name())) {
            return intercepted(signature, given);
        }
        final var parameters = new ArrayList<>();
        final int fixed = signature.varargs() ? signature.required() : given.size();
        for (int i = 0; i < fixed; i++) {
            parameters.add(argument(signature.typeOf(i), given.get(i)));
        }
        if (signature.varargs()) {
            parameters.add(variadic(signature, given, fixed));
        }
        return call(signature.name(), signature.implementation().instance(),
                signature.implementation().method(), this, parameters);
    }

    /**
     * Hands a function call to the interceptor instead of its implementation. Every argument is
     * boxed with its static type, spread rather than packed, because a mock matches on what the
     * script wrote rather than on how the Java method happens to receive it.
     */
    private Object intercepted(javax0.bubas.analyser.FunctionSignature signature,
                               List<CoreExpression> given) {
        final var values = new ArrayList<Value>();
        for (final var node : given) {
            values.add(new RuntimeValue(node.type(), evaluate(node)));
        }
        final var result = interceptor.onFunction(signature.name(), List.copyOf(values));
        if (result == null) {
            if (signature.returnType() != BubasType.VOID) {
                throw fail(signature.name() + " returns " + signature.returnType()
                        + ", but the interceptor supplied no value");
            }
            return null;
        }
        return result.as(Object.class);
    }

    /**
     * Evaluates one argument and boxes it the way its declared parameter asks for.
     * <p>
     * A wildcard parameter is the only reason boxing exists: the handler declared no concrete type,
     * so it is handed something that carries the type along with the value.
     */
    private Object argument(BubasType expected, CoreExpression node) {
        final var value = evaluate(node);
        if (expected == BubasType.ANY_ARRAY) {
            return new RuntimeArray(value, element(node.type()));
        }
        if (expected == BubasType.ANY) {
            return new RuntimeValue(node.type(), value);
        }
        return value;
    }

    /**
     * Packs the trailing arguments into the array the variadic parameter declares.
     * <p>
     * Reflection does not do this: {@code Method.invoke} on a variadic method wants the array
     * already built, and of the exact component type, so the component comes off the Java method
     * rather than from the BUBAS element type.
     */
    private Object variadic(javax0.bubas.analyser.FunctionSignature signature,
                            List<CoreExpression> given, int fixed) {
        final var method = signature.implementation().method();
        final var component = method.getParameterTypes()[method.getParameterCount() - 1]
                .getComponentType();
        final var array = java.lang.reflect.Array.newInstance(component, given.size() - fixed);
        for (int i = fixed; i < given.size(); i++) {
            java.lang.reflect.Array.set(array, i - fixed, argument(signature.typeOf(i), given.get(i)));
        }
        return array;
    }

    /**
     * Anything the embedder's code throws is wrapped with the line of the statement that called it,
     * so a failure inside a handler still points at the script.
     */
    private Object call(String what, Object instance, java.lang.reflect.Method method,
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
        } catch (ReflectiveOperationException | IllegalArgumentException e) {
            // An argument mismatch is an IllegalArgumentException, which is neither a
            // ReflectiveOperationException nor thrown by the handler — so without this it would
            // reach the embedder as a raw Java exception naming no line. It names what BUBAS calls
            // the thing rather than the Java method, which is always "call" and says nothing, and
            // it shows what actually arrived: a mismatch nobody can see is a mismatch nobody can
            // fix.
            throw new BubasException(what + " could not be called: it takes ("
                    + declared(method) + ") but was given (" + supplied(parameters) + ")",
                    current.line(), current.source(), e);
        }
    }

    private static String declared(java.lang.reflect.Method method) {
        final var types = method.getParameterTypes();
        return java.util.stream.IntStream.range(1, types.length)
                .mapToObj(i -> types[i].getSimpleName())
                .reduce((a, b) -> a + ", " + b).orElse("");
    }

    private static String supplied(List<Object> parameters) {
        return parameters.stream()
                .map(parameter -> parameter == null ? "nothing"
                        : parameter.getClass().getSimpleName())
                .reduce((a, b) -> a + ", " + b).orElse("");
    }

    private static BubasType element(BubasType arrayType) {
        return arrayType instanceof BubasType.ArrayOf(var elementType) ? elementType : arrayType;
    }

    private BubasException wrap(Throwable cause) {
        if (cause instanceof BubasException already) {
            return already;
        }
        return new BubasException(cause instanceof Mistake ? cause.getMessage()
                : cause.getClass().getSimpleName() + ": " + cause.getMessage(),
                current.line(), current.source(), cause);
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

    /**
     * One statement executed, or one pass of a loop.
     * <p>
     * A pass is not free and is not a statement: a {@code WHILE} evaluates its condition and a
     * {@code FOR} moves and tests its counter, and a program doing that a billion times is spending
     * a billion steps whatever its body contains. Counting only statements would price that at
     * whatever the body happens to cost.
     */
    private void step() {
        // Through the accessor rather than the field: what the budget is has one answer, and a
        // handler asking gets the same one the check uses.
        if (++steps > maxSteps()) {
            throw fail("this run has taken more than " + maxSteps()
                    + " steps and has been stopped");
        }
    }

    @Override
    public long maxSteps() {
        return limits.steps();
    }

    @Override
    public int maxArrayLength() {
        return limits.arrayLength();
    }

    // ------------------------------------------------------------------ context

    @Override
    public ExpressionArg expression(String name) {
        if (argument(name) instanceof CoreArgument.Lazy lazy) {
            return new LazyArgument(lazy.expression());
        }
        throw new Mistake("'" + name + "' is not an expression placeholder");
    }

    @Override
    public VariableArg variable(String name) {
        if (argument(name) instanceof CoreArgument.Slot slot) {
            return new SlotArgument(slot);
        }
        throw new Mistake("'" + name + "' is not a variable placeholder");
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

    /**
     * At most one evaluation, because the index selects which location is read or written.
     */
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
