package javax0.bubas.api;

/**
 * The index of an indexed reference such as {@code A[5]}.
 * <p>
 * It arrives unevaluated, like every expression a command receives, but it is the one with a cap:
 * <strong>at most once</strong>. It selects <em>which</em> location is read or written, so a second
 * evaluation could pick a different element and leave {@link VariableArg#get()} and
 * {@link VariableArg#set(Value)} disagreeing about what they touched. It may also have side
 * effects, and a command may legitimately never need it.
 * <p>
 * An index is always {@code INTEGER}, so there is no {@link Value} to unwrap here.
 */
public interface ArrayIndex {

    /**
     * @throws BubasException on a second call — a handler reaching for a fresh evaluation has
     *                        misunderstood the contract, and is told rather than handed a cached
     *                        answer that looks like one
     */
    void evaluate();

    /**
     * @throws BubasException unless {@link #evaluate()} has been called
     */
    long get();
}
