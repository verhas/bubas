package javax0.bubas.bunit.commands;

import javax0.bubas.api.StatementContext;

/**
 * {@code "APPROVE _" IS MOCKED}
 * <p>
 * The command is recorded and its handler never runs. A command whose pattern declares a variable
 * still writes one: an opaque target gets a token, because nothing else could go there, and every
 * other kind must be supplied — which the mock consistency checker enforces before the test runs.
 */
public final class MockCommand {

    public static final String PATTERN = "{literal/STRING:name} IS MOCKED";

    public void call(StatementContext ctx, String name) {
        Mock.recorder(ctx).mockCommand(name);
    }
}
