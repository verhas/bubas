package javax0.bubas.api;

import java.math.MathContext;

/**
 * What an implementation can reach without reaching outside the program.
 * <p>
 * The rounding policy the language was sealed with, the log, and the ability to refuse. Everything
 * here is either a property of the language or a way of saying something; nothing here is a way of
 * asking the application a question.
 * <p>
 * That is the whole point of it existing separately from {@link Context}. A function declared
 * {@link BubasMemoizable} must take this rather than a {@code Context}, so it cannot reach a service —
 * not because it promised not to and something checks, but because the method is not there to call.
 * A promise the type system keeps needs no enforcement.
 *
 * @see Context
 * @see BubasMemoizable
 */
public interface CoreContext {

    /**
     * The rounding policy for {@code DECIMAL} division, sealed into the language. One value for
     * every run of every program compiled against it.
     */
    MathContext mathContext();

    void log(String level, String message);

    void debug(String message);

    /**
     * Aborts the run. This is a control-flow operation, not a logging call, and it sits here beside
     * {@link #log} only because both are things an implementation reaches for.
     * <p>
     * In a {@link BubasMemoizable} function called while compiling, this fails the compilation at the
     * line of the call, which is what refusing an argument that can never be right should do.
     *
     * @throws BubasException always
     */
    void error(String message);
}
