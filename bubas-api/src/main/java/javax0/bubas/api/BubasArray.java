package javax0.bubas.api;

/**
 * An array whose element type is not known to the function receiving it.
 * <p>
 * It exists for exactly one case: a parameter declared {@code ANY_ARRAY}, of which {@code LENGTH}
 * is the only example in the prelude. Every array whose element type <em>is</em> known crosses into
 * Java as a native array — {@code long[]}, {@code BigDecimal[]}, {@code Order[]} — passed as the
 * interpreter's own backing store, so {@code Arrays.sort} works and an in-place reorder is visible
 * to the script.
 */
public interface BubasArray {

    int size();

    BubasType elementType();

    /** The backing store: a {@code long[]}, {@code String[]}, {@code Order[]}, and so on. */
    Object raw();
}
