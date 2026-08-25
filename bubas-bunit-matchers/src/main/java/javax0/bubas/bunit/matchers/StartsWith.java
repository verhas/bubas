package javax0.bubas.bunit.matchers;

import javax0.bubas.api.BubasType;
import javax0.bubas.api.Context;
import javax0.bubas.api.Value;
import javax0.bubas.bunit.Matcher;

/** {@code STARTS_WITH("ERR-")} — a STRING beginning with that text. */
public final class StartsWith {

    public static final String NAME = "STARTS_WITH";

    public Matcher call(Context ctx, String prefix) {
        return new Prefix(prefix);
    }

    private record Prefix(String prefix) implements Matcher {

        @Override
        public boolean matches(Value actual) {
            return actual.type() == BubasType.STRING && actual.asString().startsWith(prefix);
        }

        @Override
        public String describe() {
            return "starting with \"" + prefix + '"';
        }
    }
}
