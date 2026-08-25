package javax0.bubas.bunit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Says which of a statement's placeholders holds the name of the function or command it is about.
 * <p>
 * The framework reads this instead of recognising a statement by its keyword. It learns that
 * {@code "LOAD_ORDER" RETURNS 1} is about {@code LOAD_ORDER} because the class said the name is in
 * the placeholder called {@code name} — not because anything here knows the word {@code RETURNS}.
 * Replace the vocabulary and this module does not change.
 * <p>
 * The placeholder must be a {@code {literal/STRING:…}}, because the checker reads it before the test
 * runs and a computed name would not be there to read.
 *
 * @see DeclaresMock
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface NamesTarget {
    /** The placeholder's name, as written in the pattern. */
    String value();
}
