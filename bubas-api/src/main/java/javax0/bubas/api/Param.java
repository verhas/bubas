package javax0.bubas.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Names a BUBAS parameter when the Java parameter name will not do.
 * <p>
 * Names come from the Java parameter names when the class is compiled with {@code -parameters}.
 * They are documentation only — BUBAS calls are positional — so a class compiled without the flag
 * degrades to {@code arg0} rather than failing.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Param {
    String value();
}
