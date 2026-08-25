package javax0.bubas.bunit.matchers;

import javax0.bubas.api.Context;
import javax0.bubas.api.Value;
import javax0.bubas.bunit.Matcher;

/**
 * {@code ANYTHING()} — this argument is not what the test is about.
 * <p>
 * The most useful matcher of the set. A test naming every argument exactly says it cares about all
 * of them, and then breaks when an unrelated one changes; this says plainly which ones matter.
 */
public final class Anything {

    public static final String NAME = "ANYTHING";

    public Matcher call(Context ctx) {
        return new Any();
    }

    private record Any() implements Matcher {

        @Override
        public boolean matches(Value actual) {
            return true;
        }

        @Override
        public String describe() {
            return "anything";
        }
    }
}
