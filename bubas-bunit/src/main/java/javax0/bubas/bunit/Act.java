package javax0.bubas.bunit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks the statement that runs the subject. Everything arranging must come before it and every
 * {@link Expectation} after, which is the ordering the checker enforces without knowing that ours
 * happens to be spelled {@code RUN}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Act {
}
