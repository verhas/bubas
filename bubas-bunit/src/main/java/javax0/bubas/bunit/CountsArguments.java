package javax0.bubas.bunit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Says which placeholder holds a call whose own arguments are the arguments of the mocked call.
 * <p>
 * A statement written {@code "F" WITH ARGS(1, 2) RETURNS 3} keeps its argument count inside an
 * expression rather than in placeholders, so {@link MatchesArguments} cannot count it. This tells
 * the checker to count the arguments of the call sitting at that placeholder instead — it reads the
 * shape, never the name, so a vocabulary calling its collector something other than {@code ARGS}
 * works unchanged.
 * <p>
 * Reading expression shape is acceptable here and nowhere else: this is static analysis, done once
 * before the test runs, by the component whose job is to walk the tree. A <em>handler</em>
 * inspecting the shape of what it was given at run time is the thing to avoid — it cannot even be
 * done, since {@code ExpressionArg} offers only evaluation.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface CountsArguments {
    /** The placeholder's name, as written in the pattern. */
    String value();
}
