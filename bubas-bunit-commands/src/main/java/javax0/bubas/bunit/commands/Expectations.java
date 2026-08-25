package javax0.bubas.bunit.commands;

import javax0.bubas.bunit.MockRecorder;
import javax0.bubas.api.StatementContext;
import javax0.bubas.api.Value;

import java.util.List;

/** What the expectation statements share. */
final class Expectations {

    private Expectations() {
    }

    /**
     * An expectation before {@code RUN} can only ever be about nothing, and is far more likely to
     * be a test whose act was left out than one whose author meant it.
     */
    static void requireRun(StatementContext ctx, MockRecorder recorder, String statement) {
        if (!recorder.hasRun()) {
            ctx.error(statement + " asks what happened, but the subject has not been run yet."
                    + " Put RUN before the expectations.");
        }
    }

    static void calledWith(StatementContext ctx, String name, List<Value> expected) {
        final var recorder = Mock.recorder(ctx);
        requireRun(ctx, recorder, "WAS CALLED WITH");
        final var calls = recorder.callsTo(name);
        if (calls.isEmpty()) {
            ctx.error("expected " + name + " to be called with (" + Values.show(expected)
                    + "), but it was never called");
        }
        if (calls.stream().noneMatch(call -> Values.same(expected, call))) {
            ctx.error("expected " + name + " to be called with (" + Values.show(expected)
                    + "), but it was called with " + calls.stream()
                    .map(call -> "(" + Values.show(call) + ")")
                    .reduce((x, y) -> x + ", " + y).orElse("nothing"));
        }
    }
}
