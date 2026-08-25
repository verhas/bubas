package javax0.bubas.bunit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a statement that examines what happened, and so may only appear after the {@link Act}. An
 * expectation before the act can only ever be about nothing.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Expectation {
}
