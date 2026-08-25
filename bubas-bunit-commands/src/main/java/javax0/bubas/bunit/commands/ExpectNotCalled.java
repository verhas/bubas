package javax0.bubas.bunit.commands;

import javax0.bubas.bunit.NamesTarget;
import javax0.bubas.bunit.Expectation;
import javax0.bubas.api.StatementContext;

/** {@code "APPROVE _" WAS NOT CALLED} */
@Expectation
@NamesTarget("name")
public final class ExpectNotCalled {

    public static final String PATTERN = "{literal/STRING:name} WAS NOT CALLED";

    public void call(StatementContext ctx, String name) {
        final var recorder = Mock.recorder(ctx);
        Expectations.requireRun(ctx, recorder, "WAS NOT CALLED");
        final var calls = recorder.callsTo(name);
        if (!calls.isEmpty()) {
            ctx.error("expected " + name + " never to be called, but it was called "
                    + calls.size() + " time(s), first with (" + Values.show(calls.getFirst()) + ")");
        }
    }
}
