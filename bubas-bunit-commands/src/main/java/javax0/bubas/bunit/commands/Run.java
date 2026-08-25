package javax0.bubas.bunit.commands;

import javax0.bubas.bunit.Act;
import javax0.bubas.api.StatementContext;

/**
 * {@code RUN} — the act. Everything before it arranges, everything after it examines.
 * <p>
 * A failure in the subject is recorded rather than thrown, so that a test may expect one.
 */
@Act
public final class Run {

    public static final String PATTERN = "RUN";

    public void call(StatementContext ctx) {
        Mock.recorder(ctx).run();
    }
}
