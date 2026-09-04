package javax0.bubas.api;

/**
 * What a function implementation can reach. Deliberately not much.
 * <p>
 * A function cannot touch the variable store at all — not even to read. Its arguments arrive as
 * typed method parameters and it returns a value; that is the whole interface. A by-name read
 * would be a use the definite-assignment analysis never sees, of a type nothing checked, possibly
 * before the variable was ever assigned.
 * <p>
 * What this adds to {@link CoreContext} is the application: a service is the one thing here whose
 * answer depends on which application is running and on when it is asked. That is why a
 * {@link BubasMemoizable} function takes a {@code CoreContext} instead, and why the split is a type
 * rather than a rule — a function that cannot name a method cannot call it.
 * <p>
 * Ambient configuration that many functions share is a {@linkplain #service(Class) service}, not a
 * global.
 */
public interface Context extends CoreContext {

    <T> T service(Class<T> type);

    /** For the rare case of two services of one type: a read replica and a primary, say. */
    <T> T service(Class<T> type, String qualifier);
}
