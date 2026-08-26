package javax0.bubas.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * What this function, command or opaque type <em>means</em>, in English.
 * <p>
 * For a reader who is not looking at the Java: a subject matter expert choosing an operation, a
 * code generator being told what a vocabulary is for, a person reading an exported listing of a
 * language they did not write.
 * <p>
 * <strong>It must not state anything that can be derived.</strong> Names, parameter names and
 * types, return types, variadicity, patterns, placeholder kinds, pre- and postconditions are all
 * read off the code already and appear in an export beside this text. A description repeating them
 * adds nothing and will one day contradict them. What cannot be derived is what this is for: what
 * the operation means, when to reach for it, what it fails on.
 * <p>
 * The first sentence is the summary, as in Javadoc, so a listing can be terse where it needs to be.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface BubasDescription {
    String value();
}
