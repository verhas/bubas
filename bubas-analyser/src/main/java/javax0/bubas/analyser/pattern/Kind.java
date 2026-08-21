package javax0.bubas.analyser.pattern;

import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/** What a placeholder captures. An unnamed placeholder takes its kind's spelling as its name. */
public enum Kind {
    /**
     * A reference to storage: a variable name, optionally followed by {@code [expression]}. It
     * cannot be created, because {@code a[i]} is not a name — use {@link #IDENTIFIER} for that.
     */
    VAR,
    /**
     * A bare variable name, never indexed. The only kind that may be created, and on a creating
     * placeholder its constraint is not a check but the type the runtime declares the variable
     * with.
     */
    IDENTIFIER,
    /** A full expression, evaluated lazily by the handler. */
    EXPRESSION,
    /** A literal, required to be a compile-time constant. */
    LITERAL,
    /** A type designator. */
    TYPE;

    /** The spelling used in a pattern, which is also the implicit name of an unnamed placeholder. */
    public String spelling() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<Kind> of(String word) {
        return Stream.of(values()).filter(k -> k.spelling().equalsIgnoreCase(word)).findFirst();
    }
}
