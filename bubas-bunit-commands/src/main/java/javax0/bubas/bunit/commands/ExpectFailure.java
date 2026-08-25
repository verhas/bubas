package javax0.bubas.bunit.commands;

import javax0.bubas.api.StatementContext;

/** {@code FAILED WITH "division by zero"} — the subject was supposed to fail. */
public final class ExpectFailure {

    public static final String PATTERN = "FAILED WITH {literal/STRING:message}";

    public void call(StatementContext ctx, String message) {
        final var recorder = Mock.recorder(ctx);
        Expectations.requireRun(ctx, recorder, "FAILED WITH");
        final var failure = recorder.failure().orElse(null);
        if (failure == null) {
            ctx.error("expected the subject to fail with " + message + ", but it ran to the end");
        } else if (!failure.contains(message)) {
            ctx.error("expected the failure to mention " + message + ", but it was: " + failure);
        }
    }
}
