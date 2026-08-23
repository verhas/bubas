package javax0.bubas.support;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The statements a language would be unusable without: declaration and assignment.
 * <p>
 * They are ordinary commands. Nothing in the language privileges them, an embedder installs them
 * like any other vocabulary, and an embedder who wants different ones simply does not install
 * these. Shipping them here only spares every integration from writing declaration and assignment
 * for itself.
 */
public final class Standard {

    private Standard() {
    }

    /** Pattern to implementation, in the order an embedder would naturally read them. */
    public static final Map<String, Class<?>> STATEMENTS = statements();

    private static Map<String, Class<?>> statements() {
        final var map = new LinkedHashMap<String, Class<?>>();
        map.put(Declare.PATTERN, Declare.class);
        map.put(DeclareInitialized.PATTERN, DeclareInitialized.class);
        map.put(DeclareFinal.PATTERN, DeclareFinal.class);
        map.put(DeclareArray.PATTERN, DeclareArray.class);
        map.put(Assign.PATTERN, Assign.class);
        return java.util.Collections.unmodifiableMap(map);
    }
}
