package javax0.bubas.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that this function's answer depends on nothing but its arguments, so the compiler may
 * call it while it compiles.
 * <p>
 * Given the same arguments it must give the same answer, today and next year, on any machine, in
 * any application: no services, no clock, no files, no network, no state of its own that a call can
 * change or read.
 * <p>
 * <strong>The first of those is not a promise but a type.</strong> A memoizable function's method
 * takes a {@link CoreContext} rather than a {@link Context}, which has no {@code service} method on
 * it, so a call to one does not compile. {@code seal()} refuses such a function declaring anything
 * else, which puts the mistake at startup rather than on whichever compilation first happens to
 * know all the arguments. The rest — a clock read through a static field, a file, a cached lookup
 * — nothing can check.
 * <p>
 * What it buys is that a call on known arguments becomes the answer, and everything that follows
 * from a known value follows from the call too: {@code IF ROUND(2.5) > 3} is a condition with an
 * answer, and it is rejected like any other.
 * <p>
 * <strong>Entirely optional, and it changes what a program means.</strong> A function declared
 * memoizable that is not is a lie the compiler cannot detect: it will answer at compile time with
 * one value where a run would have produced another. When in doubt, leave it off. Nothing is lost
 * but a fold.
 * <p>
 * The compiler folds a call only when every argument is known and every parameter and the result
 * are {@code INTEGER}, {@code DECIMAL}, {@code STRING} or {@code BOOLEAN}. An array, an opaque
 * value or a wildcard is never a compile-time value, so a function taking or returning one is
 * simply never folded — declaring it static is then true and idle.
 * <p>
 * A memoizable function may still refuse: {@code ctx.error} during folding is a compile error at
 * the line of the call, which is what it should be. Answering the same way every time is what the
 * annotation claims, so a function refusing these arguments while compiling would refuse them on
 * every run; reporting it now is the same answer, earlier. {@code TO_INTEGER("twelve")} cannot be a
 * number in any run, and saying so while compiling beats waiting for one.
 * <p>
 * It may also log, and the line goes nowhere. Logging decides nothing, so it is no reason to
 * decline a fold — but the run that would have written the line may happen a thousand times or
 * never, and a compilation is not one of them.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface BubasMemoizable {
}
