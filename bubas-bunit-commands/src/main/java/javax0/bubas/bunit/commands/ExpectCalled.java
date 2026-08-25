package javax0.bubas.bunit.commands;

import javax0.bubas.bunit.NamesTarget;
import javax0.bubas.bunit.Expectation;
import javax0.bubas.api.StatementContext;

/** {@code "APPROVE _" WAS CALLED} — at least once, with any arguments. */
@Expectation
@NamesTarget("name")
public final class ExpectCalled {

    public static final String PATTERN = "{literal/STRING:name} WAS CALLED";

    public void call(StatementContext ctx, String name) {
        final var recorder = Mock.recorder(ctx);
        Expectations.requireRun(ctx, recorder, "WAS CALLED");
        if (recorder.callsTo(name).isEmpty()) {
            ctx.error("expected " + name + " to be called, but it never was");
        }
    }
}
