package javax0.bubas.analyser.pattern;

/**
 * A type constraint on a placeholder, as written. Nothing here is resolved: a {@link Named} may
 * turn out to be a built-in type, a registered opaque type or a reference to another placeholder
 * in the same pattern, and which it is cannot be known until the language is sealed.
 */
public sealed interface Constraint {

    /**
     * A bare name: {@code INTEGER}, {@code NUMBER}, an opaque type, or another placeholder's name.
     *
     * @param name  what was written
     * @param exact true when written as {@code /=T}, demanding an identical type rather than an
     *              assignment-compatible one
     */
    record Named(String name, boolean exact) implements Constraint {
    }

    /**
     * {@code /a[]} — the element type of the array bound to placeholder {@code a}.
     */
    record ElementOf(String placeholder) implements Constraint {
    }

    /**
     * {@code /ARRAY} or {@code /ARRAY/INTEGER}.
     *
     * @param element the element constraint, or {@code null} for an array of anything
     */
    record ArrayOf(Constraint element) implements Constraint {
    }
}
