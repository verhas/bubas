package javax0.bubas.bunit.matchers;

import javax0.bubas.api.Context;
import javax0.bubas.api.Value;

import java.util.List;

/**
 * {@code ARGS(42, "EU", ANYTHING())} — the arguments of a call, however many there are.
 * <p>
 * Variadic and {@code ANY}, so values and matchers mix freely and nothing has to be declared twice.
 */
public final class Args {

    public static final String NAME = "ARGS";

    public Arguments call(Context ctx, Value... parts) {
        return new Arguments(List.of(parts));
    }
}
