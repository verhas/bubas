package javax0.bubas.bunit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Says which of a statement's placeholders holds the name of a variable the statement supplies for
 * a mocked command.
 * <p>
 * A mocked command's handler never runs, so whatever its pattern would have written stays unwritten.
 * An opaque target the framework fills with a token — nothing else could go there, and the test
 * cannot construct an opaque value either. Anything else has to be supplied, and this is how a
 * statement says that it does the supplying.
 * <p>
 * Repeatable, because one statement may supply several variables at once.
 */
@Repeatable(SuppliesVariables.class)
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SuppliesVariable {
    /** The placeholder holding the variable's name, as written in the pattern. */
    String value();
}
