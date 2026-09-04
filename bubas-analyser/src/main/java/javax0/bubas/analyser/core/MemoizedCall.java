package javax0.bubas.analyser.core;

import javax0.bubas.analyser.FunctionSignature;
import javax0.bubas.api.BubasException;
import javax0.bubas.api.BubasMemoizable;
import javax0.bubas.api.CoreContext;

import java.lang.reflect.InvocationTargetException;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;

/**
 * Calls a function while compiling, when it has said that it may be.
 * <p>
 * A function is opaque to the compiler unless it carries {@link BubasMemoizable}: purity cannot be
 * inferred, and a function that reads a clock or a database must never answer at compile time. The
 * annotation is the function's own claim, and this is the only thing that acts on it.
 * <p>
 * Folding is the extreme case of what the annotation licenses. Memoizing means answering a repeated
 * call from an earlier one; this answers every call from one made before the program ever ran.
 *
 * @see BubasMemoizable
 */
final class MemoizedCall {

    private MemoizedCall() {
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
        final var owner = signature.implementation().owner();
        if (!owner.isAnnotationPresent(BubasMemoizable.class) || signature.varargs()) {
            return false;
        }
        return Constants.tracked(signature.returnType())
                && signature.parameters().stream()
                .allMatch(parameter -> Constants.tracked(parameter.type()));
    }

    /**
     * @param arguments the already-known argument values, in order
     * @return what the function answered
     * @throws Refusal when the function refuses these arguments
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
     * There is no application here and no run. A service is not among the things it could ask for,
     * because {@link CoreContext} has no such method and {@code seal()} has already refused any
     * memoizable function declaring anything else — the hole is closed by the type rather than
     * here.
     * <p>
     * The log is discarded. Logging does not decide anything, so a function has every right to do
     * it and none of it belongs to this compilation: the run that would have written the line may
     * happen a thousand times or never, and neither number is one. Refusing to fold a function
     * because it logs would punish it for something that cannot affect the answer.
     * <p>
     * {@link #error} is the opposite and is not discarded. Such a function answers the same way
     * every time, so one that refuses these arguments while compiling would refuse them on every
     * run. Reporting it now is the same answer, earlier.
     */
    private record CompileTime(String function, MathContext mathContext) implements CoreContext {

        @Override
        public MathContext mathContext() {
            return mathContext;
        }

        @Override
        public void log(String level, String message) {
            // Deliberately nowhere. See the note above the record.
        }

        @Override
        public void debug(String message) {
        }

        @Override
        public void error(String message) {
            throw new Refusal(function + ": " + message);
        }
    }
}
