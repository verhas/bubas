package javax0.bubas.bunit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Says which placeholder holds what the mocked function yields.
 * <p>
 * The checker compares its static type against the function's declared return type. Answering a
 * STRING where the subject expects a DECIMAL is otherwise discovered somewhere inside the subject,
 * as a type error about a value whose origin is no longer visible.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SuppliesResult {
    /** The placeholder's name. */
    String value();
}
