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

    /**
     * The most this may spend, counted in statements executed and loop passes taken, or
     * {@link Long#MAX_VALUE} when nothing set a limit.
     * <p>
     * The limit, not the remainder: it answers "what was I given", not "what is left". A caller
     * wanting to know how close it is would need something else, and nothing has asked yet.
     * <p>
     * Here rather than on {@link Context} because a budget belongs to whoever is doing the work, and
     * that is not always the interpreter. The compiler already calls
     * {@linkplain BubasMemoizable memoizable} functions while it compiles, and the day it works out
     * a loop whose every value it can follow, it is executing something and needs a bound on it for
     * the same reason a run does. A {@code Context} is not available there; this is.
     */
    long maxSteps();

    /**
     * The largest array that may be brought into existence, or {@link Integer#MAX_VALUE} when
     * nothing set a limit.
     * <p>
     * A command that allocates has to ask. Nothing can enforce this on its behalf: by the time an
     * array reaches a variable the memory is already spent, so the only useful place to refuse is
     * before {@code Array.newInstance}, inside the command that calls it. {@code DECLARE a[n] T}
     * asks, and a vocabulary with its own array-making statement should too.
     */
    int maxArrayLength();

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
