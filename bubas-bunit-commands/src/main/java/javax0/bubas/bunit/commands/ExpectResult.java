package javax0.bubas.bunit.commands;

import javax0.bubas.api.ExpressionArg;
import javax0.bubas.api.StatementContext;

/** {@code RESULT IS FALSE} — what the subject returned. */
public final class ExpectResult {

    public static final String PATTERN = "RESULT IS {expression:value}";

    public void call(StatementContext ctx, ExpressionArg value) {
        final var recorder = Mock.recorder(ctx);
        Expectations.requireRun(ctx, recorder, "RESULT IS");
        recorder.failure().ifPresent(failure ->
                ctx.error("the subject failed instead of returning a value: " + failure));
        final var expected = value.evaluate();
        final var actual = recorder.result().orElse(null);
        if (!Values.same(expected, actual)) {
            ctx.error("expected the result to be " + Values.show(expected)
                    + ", but it was " + Values.show(actual));
        }
    }
}
