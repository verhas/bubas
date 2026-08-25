package javax0.bubas.bunit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Says which placeholders hold the arguments of the call this statement is about, in order.
 * <p>
 * With it the checker can count: a mock declared for three arguments against a function that takes
 * one matches nothing at run time and would otherwise look like a mock that simply never fired.
 * A statement that matches whatever it is given carries no annotation at all.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface MatchesArguments {
    /** Placeholder names, in argument order. */
    String[] value();
}
