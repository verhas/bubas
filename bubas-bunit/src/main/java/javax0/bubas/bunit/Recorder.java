package javax0.bubas.bunit;

import javax0.bubas.analyser.BubasLanguage;
import javax0.bubas.analyser.BubasProgram;
import javax0.bubas.analyser.core.CoreProgram;
import javax0.bubas.api.BubasCallInterceptor;
import javax0.bubas.api.BubasException;
import javax0.bubas.api.ExpressionArg;
import javax0.bubas.api.BubasType;
import javax0.bubas.api.StatementContext;
import javax0.bubas.api.Value;
import javax0.bubas.api.VariableArg;
import javax0.bubas.runtime.Interpreter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
    /** Token names a mock answered with, so a failure can say one escaped into real code. */
    private final Set<String> tokens = new LinkedHashSet<>();
    /** Pattern source to the name a test calls it by, so the interceptor can look one up. */
    private final Map<String, String> commandNames = new LinkedHashMap<>();
    private int tokensMade;
    /** The last function the interpreter asked about and we declined. See {@link #explain}. */
    private String lastUnmocked;
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

    /**
     * A STRING given for an opaque parameter names a token, exactly as it does for a mocked
     * return. The subject's own declaration says which parameters those are.
     * <p>
     * Converting here rather than at {@link #run()} keeps the parameter's name in hand, and leaves
     * {@code run} to report only the type errors that really are type errors.
     */
    @Override
    public void argument(String name, Value value) {
        arguments.put(name, token(value, declaredType(name)));
    }

    /** The type the subject declared for that parameter, or {@code null} if it has none. */
    private BubasType declaredType(String name) {
        return subject.variables().stream()
                .limit(subject.parameterCount())
                .filter(slot -> slot.name().equals(name))
                .findFirst()
                .map(CoreProgram.Slot::type)
                .orElse(null);
    }

    @Override
    public void run() {
        hasRun = true;
        final var interpreter = Interpreter.of(subject)
                .intercept(this)
                .logger((level, message) -> log.add(level + ": " + message));
        // A token carries its BUBAS type and has no Java class to be checked against; the
        // Value overload is the way in for those. Everything else goes in as itself.
        arguments.forEach((name, value) -> {
            if (value.as(Object.class) instanceof Token) {
                interpreter.argument(name, value);
            } else {
                interpreter.argument(name, value.as(Object.class));
            }
        });
        try {
            result = interpreter.run();
        } catch (BubasException e) {
            failure = e.getDiagnostic() + explain(e);
        }
    }

    /**
     * The sentence a diagnostic cannot say for itself.
     * <p>
     * The commonest mistake in a BUNIT test is mocking a function that yields an opaque value and
     * leaving the ones that consume it real. The token then reaches a handler expecting the domain
     * class, and the interpreter reports an argument mismatch — true, and no help at all, because
     * nothing in it says where the strange value came from. Opacity is total, so mocking has to be
     * too: that is the thing to say.
     */
    private String explain(BubasException failure) {
        if (tokensMade == 0 && tokens.isEmpty()) {
            return "";
        }
        if (lastUnmocked == null || !failure.getMessage().contains("could not be called")) {
            return "";
        }
        return "\n\n'" + lastUnmocked + "' is not mocked, and it was given a token — the stand-in a"
                + " mock uses for a value BUBAS cannot construct. A token can only reach a mocked"
                + " function, so every function taking that opaque type has to be mocked as well.";
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
        final var mocked = functions.containsKey(canonical(name));
        if (!mocked) {
            // Asked before every call, so the last one declined is the one about to run for real —
            // and if it fails, the one worth naming.
            lastUnmocked = name;
        }
        return mocked;
    }

    @Override
    public Value onFunction(String name, List<Value> arguments) {
        record(name, arguments);
        return functions.get(canonical(name)).stream()
                .filter(stub -> stub.anyArguments()
                        || Matching.same(stub.arguments(), arguments))
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
                .map(Matching::show).reduce((a, b) -> a + ", " + b).orElse("") + ")");
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
        if (given != null && Token.named(expected, given.type())) {
            tokens.add(given.asString());
            return new Boxed(expected, new Token(given.asString()));
        }
        return given;
    }

    private static String canonical(String name) {
        return name.toUpperCase(java.util.Locale.ROOT);
    }
}
