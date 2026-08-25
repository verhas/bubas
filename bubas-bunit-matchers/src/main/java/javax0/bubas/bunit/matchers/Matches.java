package javax0.bubas.bunit.matchers;

import javax0.bubas.api.BubasType;
import javax0.bubas.api.Context;
import javax0.bubas.api.Value;
import javax0.bubas.bunit.Matcher;

import java.util.regex.Pattern;

/**
 * {@code MATCHES("ORD-[0-9]+")} — a STRING the whole of which fits the expression.
 * <p>
 * The whole value, not part of it: {@code CONTAINS} is the matcher for a fragment, and a regular
 * expression that quietly matched a substring would be the source of a class of tests that pass for
 * the wrong reason.
 * <p>
 * Worth using sparingly. BUBAS exists so that a domain expert can read the rule, and a regular
 * expression is a second language embedded in the first — the one matcher here that a reader may
 * not be able to check. Prefer {@link StartsWith}, {@link EndsWith} or {@link Contains} when they
 * say enough.
 * <p>
 * The expression is compiled when the matcher is built, so a malformed one fails while the test is
 * arranging itself rather than in the middle of comparing a call.
 */
public final class Matches {

    public static final String NAME = "MATCHES";

    public Matcher call(Context ctx, String expression) {
        return new Regular(Pattern.compile(expression), expression);
    }

    private record Regular(Pattern pattern, String source) implements Matcher {

        @Override
        public boolean matches(Value actual) {
            return actual.type() == BubasType.STRING && pattern.matcher(actual.asString()).matches();
        }

        @Override
        public String describe() {
            return "matching \"" + source + '"';
        }
    }
}
