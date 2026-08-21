package javax0.bubas.analyser.pattern;

import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/** What a pattern guarantees about a variable after the statement has run. */
public enum Postcondition {
    DECLARED,
    INITIALIZED,
    /** Implies the variable is newly created and initialized, so it stands alone. */
    FINAL;

    public String spelling() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<Postcondition> of(String word) {
        return Stream.of(values()).filter(p -> p.name().equalsIgnoreCase(word)).findFirst();
    }
}
