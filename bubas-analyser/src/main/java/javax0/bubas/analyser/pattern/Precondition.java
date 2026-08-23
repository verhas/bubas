package javax0.bubas.analyser.pattern;

import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * What a pattern requires of a variable before the statement runs.
 * <p>
 * There are two independent axes. {@code ADD 50.50 TO total} needs {@code total} to be both
 * readable and writable, which one slot could not express.
 */
public enum Precondition {
    NEW(Axis.ASSIGNMENT),
    DECLARED(Axis.ASSIGNMENT),
    INITIALIZED(Axis.ASSIGNMENT),
    MUTABLE(Axis.MUTABILITY),
    FINAL(Axis.MUTABILITY);

    public enum Axis {ASSIGNMENT, MUTABILITY}

    private final Axis axis;

    Precondition(Axis axis) {
        this.axis = axis;
    }

    public Axis axis() {
        return axis;
    }

    public String spelling() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<Precondition> of(String word) {
        return Stream.of(values()).filter(p -> p.name().equalsIgnoreCase(word)).findFirst();
    }
}
