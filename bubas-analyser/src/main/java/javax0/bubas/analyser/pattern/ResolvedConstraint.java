package javax0.bubas.analyser.pattern;

import javax0.bubas.api.BubasType;

/**
 * A constraint after {@code seal()} has worked out what its names meant. A {@link Constraint} is
 * what was written; this is what it refers to.
 */
public sealed interface ResolvedConstraint {

    /**
     * A concrete type.
     *
     * @param exact written {@code /=T}: the type must be identical rather than assignable
     */
    record Type(BubasType type, boolean exact) implements ResolvedConstraint {
    }

    /** {@code NUMBER}: an INTEGER or a DECIMAL. Not a type anything can have. */
    record Numeric() implements ResolvedConstraint {
    }

    /** {@code /T}: whatever another placeholder in the same pattern turns out to be. */
    record Reference(String placeholder, boolean exact) implements ResolvedConstraint {
    }

    /** {@code /a[]}: the element type of another placeholder's array. */
    record Element(String placeholder) implements ResolvedConstraint {
    }

    /**
     * {@code /ARRAY} or {@code /ARRAY/x}.
     *
     * @param element {@code null} for an array of anything
     */
    record Array(ResolvedConstraint element) implements ResolvedConstraint {
    }
}
