package javax0.bubas.analyser;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The words the core language owns. Everything else that is reserved — pattern literals, function
 * names, opaque type names — arrives at registration and is not known here.
 */
public final class Keywords {

    private Keywords() {
    }

    /**
     * Keywords that drive block parsing. No statement pattern may begin with one of these, because
     * the core parser recognises them structurally rather than by matching a line. A DSL author who
     * wants a conditional writes {@code WHEN}, not {@code IF}.
     */
    public static final Set<String> STRUCTURAL = Set.of(
            "PROGRAM", "IF", "ELSEIF", "ELSE", "DO", "WHILE", "UNTIL",
            "FOR", "EXIT", "RETURN", "END");

    /**
     * Words that may appear inside an expression. They matter to the matcher because an expression
     * placeholder stops at a reserved word — but not at one of these, which is why a pattern
     * literal spelled {@code AND} cannot follow an expression placeholder.
     */
    public static final Set<String> EXPRESSION = Set.of("AND", "OR", "NOT", "MOD", "TRUE", "FALSE");

    /** The built-in scalar type names. Opaque type names join them at registration. */
    public static final Set<String> SCALAR_TYPES = Set.of("INTEGER", "DECIMAL", "STRING", "BOOLEAN");

    /** Words that appear in block syntax without driving it. */
    public static final Set<String> CLAUSE = Set.of("THEN", "TO", "STEP", "RETURNS");

    /** Everything the core language reserves. */
    public static final Set<String> CORE = Stream.of(STRUCTURAL, EXPRESSION, SCALAR_TYPES, CLAUSE)
            .flatMap(Set::stream)
            .collect(HashSet::new, HashSet::add, HashSet::addAll);

    public static boolean isStructural(String word) {
        return STRUCTURAL.contains(canonical(word));
    }

    public static boolean isExpressionWord(String word) {
        return EXPRESSION.contains(canonical(word));
    }

    public static String canonical(String word) {
        return word.toUpperCase(Locale.ROOT);
    }
}
