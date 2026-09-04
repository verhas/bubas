package javax0.bubas.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that this command puts one placeholder's value into another, unchanged.
 * <p>
 * A pattern already says which variables a command <em>writes</em>, through its postconditions. It
 * cannot say what it writes: {@code ROUTE claim TO approver} writes {@code approver} with a value
 * nothing could predict, and so does every command that computes rather than copies. This says the
 * value is exactly the named argument, which is what lets the compiler know that after
 * {@code n = 5} the variable holds 5.
 * <p>
 * <strong>Entirely optional.</strong> Nothing needs it and nothing behaves differently without it,
 * except that the compiler learns nothing about the variable and treats its value as unknown from
 * that point on — the same as for any command that computes. Declaring it is how an embedder's own
 * assignment syntax joins in; the analyser knows no vocabulary and asks the command.
 * <p>
 * It is a claim the implementation has to honour. A command that declares this and then writes
 * something else has lied to an analysis, and the compiler cannot tell.
 * <p>
 * An indexed target — {@code a[i] = 5} — assigns an element rather than the variable, and is
 * ignored here for that reason.
 * <p>
 * <strong>Repeatable</strong>, because one statement may write several variables:
 * {@code SPLIT name INTO first AND last} fills two, and a command saying so for one of them and
 * staying silent about the other is describing itself accurately. Each occurrence names one target,
 * and no two may name the same one. Several targets may draw on the same value.
 */
@Repeatable(BubasAssignments.class)
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface BubasAssigns {

    /** The name of the {@code var} or {@code identifier} placeholder that is written. */
    String target();

    /** The name of the {@code expression} or {@code literal} placeholder supplying the value. */
    String value();
}
