package javax0.bubas.api;

/**
 * A variable a command's pattern named.
 * <p>
 * There is no {@code declare()}: a placeholder that creates a variable must carry a type
 * constraint, so name, type and finality are all fixed statically and the runtime creates the slot
 * before the handler runs. Declaring is the framework's job; only the value needs the handler.
 * <p>
 * There is no {@code isInitialized()} either. The analyser governs that: a handler may read exactly
 * where the pattern declared an {@code initialized} precondition, and where it writes, prior state
 * is irrelevant. {@link #type()} and {@link #isFinal()} look like the same kind of member but are
 * not — a pattern can leave both open, and the two cases cannot be split into separate patterns
 * because their token shapes are identical and overlap analysis rejects the pair.
 * <p>
 * There is deliberately no {@code get(index)} or {@code set(index, value)}. A reference names one
 * location and can reach no other: given {@code MODIFY A[5]} the handler alters {@code A[5]} and
 * has no way to reach {@code A[6]}. That is the guarantee the script author reads off the line.
 */
public interface VariableArg {

    /** The script variable's name, for diagnostics. */
    String name();

    /** The reference's type — the element type when it is indexed. */
    BubasType type();

    boolean isFinal();

    boolean isIndexed();

    /**
     * @throws BubasException unless {@link #isIndexed()}. Always the same instance, so the
     *                        at-most-once cap holds however often it is asked for.
     */
    ArrayIndex index();

    /**
     * @throws BubasException when the reference is indexed and its index has not been evaluated
     */
    Value get();

    void set(Value value);

    void set(Object javaValue);
}
