package javax0.bubas.bunit;

import javax0.bubas.analyser.BubasLanguage;
import javax0.bubas.analyser.BubasProgram;
import javax0.bubas.api.BubasCallInterceptor;
import javax0.bubas.api.BubasException;
import javax0.bubas.api.ExpressionArg;
import javax0.bubas.api.BubasType;
import javax0.bubas.api.StatementContext;
import javax0.bubas.api.Value;
import javax0.bubas.api.VariableArg;
import javax0.bubas.runtime.Interpreter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Holds what a test declared and answers for it while the subject runs.
 * <p>
 * One object plays both parts: {@link MockRecorder} facing the test's statements, and
 * {@link BubasCallInterceptor} facing the subject's interpreter. They are two views of the same
 * table, which is why the seam between the two modules can be as narrow as it is.
 */
final class Recorder implements MockRecorder, BubasCallInterceptor {

    /** One declared answer. Empty arguments mean it answers whatever it is given. */
    private record Stub(List<Value> arguments, Value result, boolean anyArguments) {
    }

    private final BubasLanguage language;
    private final BubasProgram subject;
    private final Map<String, List<Stub>> functions = new LinkedHashMap<>();
    private final Set<String> mockedCommands = new LinkedHashSet<>();
    /** Command, then the placeholder it writes, to what the mock says goes there. */
    private final Map<String, Map<String, Value>> supplied = new LinkedHashMap<>();
    private final Map<String, Value> arguments = new LinkedHashMap<>();
    private final Map<String, List<List<Value>>> calls = new LinkedHashMap<>();
    private final List<String> transcript = new ArrayList<>();
    private final List<String> log = new ArrayList<>();
    /** Pattern source to the name a test calls it by, so the interceptor can look one up. */
    private final Map<String, String> commandNames = new LinkedHashMap<>();
    private int tokensMade;
    private boolean hasRun;
    private Value result;
    private String failure;

    Recorder(BubasLanguage language, BubasProgram subject) {
        this.language = language;
        this.subject = subject;
        language.commands().forEach(command ->
                commandNames.put(command.pattern().source(), command.name()));
    }

    // ---------------------------------------------------------------- the test's side

    @Override
    public void mockFunction(String name, List<Value> arguments, Value result) {
        final var signature = language.function(name).orElse(null);
        final var declared = new ArrayList<Value>();
        for (int i = 0; i < arguments.size(); i++) {
            declared.add(signature == null ? arguments.get(i)
                    : token(arguments.get(i), signature.typeOf(i)));
        }
        functions.computeIfAbsent(canonical(name), ignored -> new ArrayList<>())
                .add(new Stub(List.copyOf(declared),
                        signature == null ? result : token(result, signature.returnType()),
                        arguments.isEmpty()));
    }

    @Override
    public void mockCommand(String name) {
        mockedCommands.add(canonical(name));
    }

    @Override
    public void supplyVariable(String command, String placeholder, Value value) {
        supplied.computeIfAbsent(canonical(command), ignored -> new LinkedHashMap<>())
                .put(placeholder, value);
    }

    @Override
    public void argument(String name, Value value) {
        arguments.put(name, value);
    }

    @Override
    public void run() {
        hasRun = true;
        final var interpreter = Interpreter.of(subject)
                .intercept(this)
                .logger((level, message) -> log.add(level + ": " + message));
        arguments.forEach((name, value) -> interpreter.argument(name, value.as(Object.class)));
        try {
            result = interpreter.run();
        } catch (BubasException e) {
            failure = e.getDiagnostic();
        }
    }

    @Override
    public boolean hasRun() {
        return hasRun;
    }

    @Override
    public Optional<Value> result() {
        return Optional.ofNullable(result);
    }

    @Override
    public Optional<String> failure() {
        return Optional.ofNullable(failure);
    }

    @Override
    public List<List<Value>> callsTo(String name) {
        return calls.getOrDefault(canonical(name), List.of());
    }

    // ---------------------------------------------------------------- the subject's side

    @Override
    public boolean interceptsFunction(String name) {
        return functions.containsKey(canonical(name));
    }

    @Override
    public Value onFunction(String name, List<Value> arguments) {
        record(name, arguments);
        return functions.get(canonical(name)).stream()
                .filter(stub -> stub.anyArguments() || same(stub.arguments(), arguments))
                .findFirst()
                .map(Stub::result)
                .orElse(null);
    }

    @Override
    public boolean interceptsCommand(String pattern) {
        final var name = commandNames.get(pattern);
        return name != null && mockedCommands.contains(canonical(name));
    }

    @Override
    public void onCommand(String pattern, StatementContext context, Map<String, Object> given) {
        final var name = commandNames.get(pattern);
        final var recorded = new ArrayList<Value>();
        // In placeholder order, so that WAS CALLED WITH lines up with what the statement reads.
        final var writes = supplied.getOrDefault(canonical(name), Map.of());
        for (final var entry : given.entrySet()) {
            final var argument = entry.getValue();
            recorded.add(switch (argument) {
                case VariableArg variable -> written(variable, writes.get(entry.getKey()));
                case ExpressionArg expression -> expression.evaluate();
                case Value value -> value;
                case null -> null;
                default -> new Boxed(BubasType.STRING, String.valueOf(argument));
            });
        }
        record(name, recorded);
    }

    // ---------------------------------------------------------------- reporting

    List<String> transcript() {
        return transcript;
    }

    List<String> log() {
        return log;
    }

    private void record(String name, List<Value> arguments) {
        calls.computeIfAbsent(canonical(name), ignored -> new ArrayList<>())
                .add(List.copyOf(arguments));
        transcript.add(name + "(" + arguments.stream()
                .map(Recorder::show).reduce((a, b) -> a + ", " + b).orElse("") + ")");
    }

    /**
     * Approach three: an opaque target is written automatically, because a token is the only thing
     * that could go there — the test cannot construct an opaque value either. Anything else the
     * command declares must be supplied by the mock, which the consistency checker will enforce;
     * until it does, an unwritten slot reads as nothing rather than failing here.
     */
    private Value written(VariableArg variable, Value supplied) {
        if (supplied != null) {
            variable.set(supplied.as(Object.class));
            return supplied;
        }
        if (variable.type() instanceof BubasType.Opaque) {
            final var token = new Boxed(variable.type(), new Token("#" + ++tokensMade));
            variable.set(token.raw());
            return token;
        }
        try {
            return variable.get();
        } catch (RuntimeException unset) {
            return null;
        }
    }

    /**
     * A STRING where an opaque value is expected names a token: opaque values are the only ones
     * BUBAS cannot construct, so nothing else could have been meant.
     */
    private Value token(Value given, BubasType expected) {
        if (given != null && expected instanceof BubasType.Opaque
                && given.type() == BubasType.STRING) {
            return new Boxed(expected, new Token(given.asString()));
        }
        return given;
    }

    private static boolean same(List<Value> expected, List<Value> actual) {
        if (expected.size() != actual.size()) {
            return false;
        }
        for (int i = 0; i < expected.size(); i++) {
            final var a = expected.get(i) == null ? null : expected.get(i).as(Object.class);
            final var b = actual.get(i) == null ? null : actual.get(i).as(Object.class);
            if (a instanceof BigDecimal one && b instanceof BigDecimal other) {
                if (one.compareTo(other) != 0) {
                    return false;
                }
            } else if (!Objects.equals(a, b)) {
                return false;
            }
        }
        return true;
    }

    private static String show(Value value) {
        final var raw = value == null ? null : value.as(Object.class);
        return raw instanceof String text ? '"' + text + '"' : String.valueOf(raw);
    }

    private static String canonical(String name) {
        return name.toUpperCase(java.util.Locale.ROOT);
    }
}
