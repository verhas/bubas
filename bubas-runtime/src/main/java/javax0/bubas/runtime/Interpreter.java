package javax0.bubas.runtime;

import javax0.bubas.analyser.BubasProgram;
import javax0.bubas.api.BubasException;
import javax0.bubas.api.BubasType;
import javax0.bubas.api.Value;

import java.math.MathContext;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * One run of one program.
 * <p>
 * Deliberately cheap and deliberately single-use. Compiling is the expensive part and a
 * {@link BubasProgram} is reusable, so a run gets nothing but a fresh variable store and whatever
 * varies per run — the arguments, the run-scoped services, the rounding policy.
 * <p>
 * Not thread-safe, and not meant to be: concurrent orchestration means one interpreter per thread,
 * all sharing one program.
 */
public final class Interpreter {

    private final BubasProgram program;
    private final Object[] slots;
    private final Map<Class<?>, Map<String, Object>> services = new HashMap<>();
    private final Map<String, Integer> parameters = new LinkedHashMap<>();
    private final java.util.Set<String> supplied = new java.util.HashSet<>();
    private MathContext mathContext = MathContext.DECIMAL128;
    private BiConsumer<String, String> logger = (level, message) ->
            System.out.println(level + ": " + message);
    private javax0.bubas.api.BubasCallInterceptor interceptor;
    private boolean spent;

    private Interpreter(BubasProgram program) {
        this.program = program;
        this.slots = new Object[program.variables().size()];
        // A mutable copy: the language's own maps are immutable, and registerService writes here.
        program.language().services().forEach((type, byQualifier) ->
                services.put(type, new HashMap<>(byQualifier)));
        for (int i = 0; i < program.parameterCount(); i++) {
            parameters.put(program.variables().get(i).name(), i);
        }
    }

    public static Interpreter of(BubasProgram program) {
        return new Interpreter(program);
    }

    /**
     * Supplies one of the program's parameters.
     *
     * @throws BubasException when the program has no such parameter, or the value is of the wrong
     *                        type — checked here rather than at the first use, so a wiring mistake
     *                        surfaces before anything runs
     */
    public Interpreter argument(String name, Object value) {
        final var slot = parameters.get(name);
        if (slot == null) {
            throw new BubasException(program.name() + " has no parameter named '" + name + "'",
                    0, "");
        }
        final var declared = program.variables().get(slot).type();
        if (!accepts(declared, value)) {
            throw new BubasException("'" + name + "' is " + declared + ", so it cannot be given a "
                    + (value == null ? "null" : value.getClass().getSimpleName()), 0, "");
        }
        slots[slot] = value;
        supplied.add(name);
        return this;
    }

    /**
     * Supplies a parameter with a value that already carries its own BUBAS type.
     * <p>
     * For every kind of value a program can construct, {@link #argument(String, Object)} is the way
     * in and the Java class is the check. An opaque value is the exception: a caller may hold
     * something that stands for a domain object without being one — a test's stand-in, most
     * obviously, since an opaque value is the only kind BUBAS cannot construct. There is no Java
     * class to check such a thing against, and the honest check is the BUBAS type it declares.
     * <p>
     * The declared type still has to match, so this trusts the caller about the representation and
     * about nothing else.
     */
    public Interpreter argument(String name, Value value) {
        final var slot = parameters.get(name);
        if (slot == null) {
            throw new BubasException(program.name() + " has no parameter named '" + name + "'",
                    0, "");
        }
        final var declared = program.variables().get(slot).type();
        if (value == null || !declared.equals(value.type())) {
            throw new BubasException("'" + name + "' is " + declared + ", so it cannot be given a "
                    + (value == null ? "null" : value.type()), 0, "");
        }
        slots[slot] = value.as(Object.class);
        supplied.add(name);
        return this;
    }

    public <T> Interpreter registerService(Class<T> type, T service) {
        return registerService(type, "", service);
    }

    public <T> Interpreter registerService(Class<T> type, String qualifier, T service) {
        services.computeIfAbsent(type, ignored -> new HashMap<>()).put(qualifier, service);
        return this;
    }

    /**
     * Substitutes behaviour for functions and commands, for a test framework and nothing else.
     * Installed per run, so the same program runs against real implementations when none is given.
     */
    public Interpreter intercept(javax0.bubas.api.BubasCallInterceptor interceptor) {
        this.interceptor = interceptor;
        return this;
    }

    /** The rounding policy for {@code DECIMAL} division. Defaults to {@code DECIMAL128}. */
    public Interpreter mathContext(MathContext mathContext) {
        this.mathContext = mathContext;
        return this;
    }

    /** Where {@code ctx.log} goes. Defaults to standard output. */
    public Interpreter logger(BiConsumer<String, String> logger) {
        this.logger = logger;
        return this;
    }

    /**
     * @return the program's result, or {@code null} when it declares no {@code RETURNS}
     * @throws BubasException on any failure, or when a parameter was never supplied, or when this
     *                        interpreter has already run
     */
    public Value run() {
        if (spent) {
            throw new BubasException("an interpreter runs once; compile once and run many",
                    0, "");
        }
        spent = true;
        // Tracked rather than inferred from null: an opaque parameter may legitimately be null.
        for (final var name : parameters.keySet()) {
            if (!supplied.contains(name)) {
                throw new BubasException(program.name() + " needs an argument for '" + name + "'",
                        0, "");
            }
        }
        final var result = new Machine(program.core(), slots, services, mathContext, logger,
                interceptor).run();
        return program.returns() == null ? null : new RuntimeValue(program.returns(), result);
    }

    private static boolean accepts(BubasType declared, Object value) {
        if (value == null) {
            return declared instanceof BubasType.Opaque;
        }
        final var wanted = declared.javaType();
        if (wanted == long.class) {
            return value instanceof Long;
        }
        if (wanted == boolean.class) {
            return value instanceof Boolean;
        }
        return wanted.isInstance(value);
    }
}
