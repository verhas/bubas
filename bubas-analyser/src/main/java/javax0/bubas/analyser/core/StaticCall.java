package javax0.bubas.analyser.core;

import javax0.bubas.analyser.FunctionSignature;
import javax0.bubas.api.BubasException;
import javax0.bubas.api.BubasStatic;
import javax0.bubas.api.BubasType;
import javax0.bubas.api.Context;

import java.lang.reflect.InvocationTargetException;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;

/**
 * Calls a function while compiling, when it has said that it may be.
 * <p>
 * A function is opaque to the compiler unless it carries {@link BubasStatic}: purity cannot be
 * inferred, and a function that reads a clock or a database must never answer at compile time. The
 * annotation is the function's own claim, and this is the only thing that acts on it.
 *
 * @see BubasStatic
 */
final class StaticCall {

    private StaticCall() {
    }

    /**
     * Whether a call on known arguments may be answered now.
     * <p>
     * Beyond the declaration, everything crossing the boundary has to be a value a compiled program
     * can hold. An array is a store, an opaque value is a Java object and a wildcard is neither, so
     * a signature mentioning one is never folded however pure it is. Variadic calls are left out
     * because their arguments are marshalled into an array, which is that same boundary.
     */
    static boolean foldable(FunctionSignature signature) {
        if (!signature.implementation().owner().isAnnotationPresent(BubasStatic.class)
                || signature.varargs()) {
            return false;
        }
        return Constants.tracked(signature.returnType())
                && signature.parameters().stream()
                .allMatch(parameter -> Constants.tracked(parameter.type()));
    }

    /**
     * @param arguments the already-known argument values, in order
     * @return what the function answered
     * @throws BubasException when the function refuses, or asks for something a compiler has not got
     */
    static Object of(FunctionSignature signature, List<Object> arguments, MathContext mathContext) {
        final var parameters = new ArrayList<Object>(arguments.size() + 1);
        parameters.add(new CompileTime(signature.name(), mathContext));
        parameters.addAll(arguments);
        try {
            return signature.implementation().method()
                    .invoke(signature.implementation().instance(), parameters.toArray());
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException already) {
                throw already;
            }
            throw new Refusal(signature.name() + " failed while being folded: " + e.getCause());
        } catch (ReflectiveOperationException | IllegalArgumentException e) {
            throw new Refusal(signature.name() + " could not be called while compiling: " + e);
        }
    }

    /** Carries a message out of a folded call so the caller, which knows the line, can place it. */
    static final class Refusal extends RuntimeException {

        Refusal(String message) {
            super(message);
        }
    }

    /**
     * What a function is handed when the compiler calls it.
     * <p>
     * There is no application here and no run: a service would be an object this program has not got
     * yet, and a log line would be written while compiling rather than while running. Asking for
     * either is how a function that is not static gives itself away, so both say so plainly instead
     * of returning something empty.
     */
    private record CompileTime(String function, MathContext mathContext) implements Context {

        @Override
        public <T> T service(Class<T> type) {
            throw refusal(type.getSimpleName());
        }

        @Override
        public <T> T service(Class<T> type, String qualifier) {
            throw refusal(type.getSimpleName() + " '" + qualifier + "'");
        }

        @Override
        public MathContext mathContext() {
            return mathContext;
        }

        @Override
        public void log(String level, String message) {
            throw refusal("the log");
        }

        @Override
        public void debug(String message) {
            throw refusal("the log");
        }

        @Override
        public void error(String message) {
            throw new Refusal(function + ": " + message);
        }

        private Refusal refusal(String what) {
            return new Refusal(function + " is declared @BubasStatic but asked for " + what
                    + " while being folded. A static function answers from its arguments alone;"
                    + " remove the annotation or the dependency.");
        }
    }
}
