package javax0.bubas.bunit.matchers;

import javax0.bubas.api.BubasType;
import javax0.bubas.api.Context;
import javax0.bubas.api.Value;
import javax0.bubas.bunit.Matcher;

/** {@code ENDS_WITH("-EU")} — a STRING ending with that text. */
public final class EndsWith {

    public static final String NAME = "ENDS_WITH";

    public Matcher call(Context ctx, String suffix) {
        return new Suffix(suffix);
    }

    private record Suffix(String suffix) implements Matcher {

        @Override
        public boolean matches(Value actual) {
            return actual.type() == BubasType.STRING && actual.asString().endsWith(suffix);
        }

        @Override
        public String describe() {
            return "ending with \"" + suffix + '"';
        }
    }
}
