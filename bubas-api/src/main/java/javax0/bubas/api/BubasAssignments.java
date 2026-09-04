package javax0.bubas.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Holds the several {@link BubasAssigns} of a command that writes more than one variable.
 * <p>
 * Never written by hand: repeating {@code @BubasAssigns} produces this. Read through
 * {@code getAnnotationsByType}, which returns the same list whether one was written or many.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface BubasAssignments {

    BubasAssigns[] value();
}
