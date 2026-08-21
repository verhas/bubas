package javax0.bubas.analyser;

import java.util.Locale;
import java.util.Set;

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

    public static boolean isStructural(String word) {
        return STRUCTURAL.contains(word.toUpperCase(Locale.ROOT));
    }
}
