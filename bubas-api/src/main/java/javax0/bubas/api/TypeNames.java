package javax0.bubas.api;

import java.util.Set;

/**
 * The names of the built-in types, as a BUBAS author writes them.
 * <p>
 * These strings are the language's own spelling, and they are read in several places that must
 * agree: the reserved-word set, the matcher deciding what a {@code /INTEGER} constraint admits, the
 * parser turning a type name into a {@link BubasType}, and the diagnostics that name a type back to
 * the reader. A literal in each of those is a spelling nobody is keeping in step.
 * <p>
 * They are {@code String} constants rather than derived from {@link BubasType} because most uses
 * are {@code switch} labels, and a case label must be a compile-time constant. The coupling runs
 * the other way instead: {@link BubasType.Scalar} takes its own name from its enum constant, so a
 * test asserts that the two agree.
 */
public final class TypeNames {

    private TypeNames() {
    }

    public static final String INTEGER = "INTEGER";
    public static final String DECIMAL = "DECIMAL";
    public static final String STRING = "STRING";
    public static final String BOOLEAN = "BOOLEAN";

    /**
     * Not a type: a constraint admitting either numeric type, so {@code {literal/NUMBER:n}} takes
     * {@code 5} and {@code 5.0} alike.
     */
    public static final String NUMBER = "NUMBER";

    /** The element-agnostic array constraint, and how {@link BubasType#ANY_ARRAY} reads. */
    public static final String ARRAY = "ARRAY";

    /**
     * How {@link BubasType#ANY} reads in a signature. Unlike the others this cannot be written in
     * BUBAS source at all — a wildcard is a Java parameter's doing, never a script's.
     */
    public static final String ANY = "ANY";

    /** The four types a script can name. {@code VOID} is not among them: nothing may be declared it. */
    public static final Set<String> SCALARS = Set.of(INTEGER, DECIMAL, STRING, BOOLEAN);
}
