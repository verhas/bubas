package javax0.bubas.bunit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Says which placeholder holds the name of a subject parameter this statement supplies.
 * <p>
 * A misspelled parameter is otherwise found only when the subject is already running, by which
 * point the mocks are set up and the reader is looking in the wrong place. The checker resolves it
 * against the subject's own parameter list instead, before anything runs.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface NamesParameter {
    /** The placeholder's name, which must be a {@code {literal/STRING:…}}. */
    String value();
}
