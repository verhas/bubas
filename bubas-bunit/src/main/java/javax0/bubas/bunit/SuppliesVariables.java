package javax0.bubas.bunit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Holds the repetitions of {@link SuppliesVariable}. Not written by hand. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SuppliesVariables {
    SuppliesVariable[] value();
}
