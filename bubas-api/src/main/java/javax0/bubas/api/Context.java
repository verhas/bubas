package javax0.bubas.api;

import java.math.MathContext;

/**
 * What a function implementation can reach. Deliberately not much.
 * <p>
 * A function cannot touch the variable store at all — not even to read. Its arguments arrive as
 * typed method parameters and it returns a value; that is the whole interface. A by-name read
 * would be a use the definite-assignment analysis never sees, of a type nothing checked, possibly
 * before the variable was ever assigned.
 * <p>
 * Ambient configuration that many functions share is a {@linkplain #service(Class) service}, not a
 * global.
 */
public interface Context {

    <T> T service(Class<T> type);

    /** For the rare case of two services of one type: a read replica and a primary, say. */
    <T> T service(Class<T> type, String qualifier);

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
     *
     * @throws BubasException always
     */
    void error(String message);
}
