package javax0.bubas.bunit.matchers;

import javax0.bubas.api.BubasType;
import javax0.bubas.api.Context;
import javax0.bubas.api.Value;
import javax0.bubas.bunit.Matcher;

/**
 * {@code CONTAINS("over limit")} — a STRING with that text somewhere in it.
 * <p>
 * The commonest string expectation by a wide margin: a message, a reason, a code. Asserting the
 * whole text couples a test to wording that is allowed to change.
 */
public final class Contains {

    public static final String NAME = "CONTAINS";

    public Matcher call(Context ctx, String text) {
        return new Substring(text);
    }

    private record Substring(String text) implements Matcher {

        @Override
        public boolean matches(Value actual) {
            return actual.type() == BubasType.STRING && actual.asString().contains(text);
        }

        @Override
        public String describe() {
            return "containing \"" + text + '"';
        }
    }
}
