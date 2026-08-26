package javax0.bubas.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Records that a {@link BubasDescription} was read against the shape of the thing it describes.
 * <p>
 * A description goes stale silently: the class gains a method, loses one, changes a signature, and
 * the sentences that were true last year quietly are not. Sealing compares the current public
 * surface against the checksum recorded here and refuses a language whose descriptions were
 * reviewed against something else.
 * <p>
 * Three states, and the difference matters:
 * <ul>
 *   <li><b>Annotation absent</b> — nothing is checked. Reviewing is opt-in per class, so a project
 *       that has not adopted it pays nothing.</li>
 *   <li><b>Empty value</b> — the first time. Sealing reports the checksum to write here and does
 *       not ask anybody to review anything, because there is nothing yet to compare against.</li>
 *   <li><b>A value</b> — checked, and a mismatch names what to re-read.</li>
 * </ul>
 * The checksum is reported rather than written for you. A build that edits its own sources to make
 * itself pass has stopped being a check.
 * <p>
 * What it catches is a change of <em>shape</em>. A function whose behaviour changed and whose
 * signature did not moves no checksum, and no mechanism here reaches that — a green checksum means
 * the description was reviewed against this shape, not that it is true.
 *
 * @see Surface
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface BubasReviewed {
    /** The checksum of the described class's public surface, or empty the first time. */
    String value();
}
