package javax0.bubas.bunit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a statement that puts its {@link NamesTarget target} into the mocked set.
 * <p>
 * A statement may name a target without mocking it — an expectation does — so declaring is its own
 * fact rather than something inferred from naming one.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface DeclaresMock {
}
