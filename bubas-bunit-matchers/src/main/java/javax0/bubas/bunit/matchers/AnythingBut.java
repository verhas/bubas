package javax0.bubas.bunit.matchers;

import javax0.bubas.api.Context;
import javax0.bubas.api.Value;
import javax0.bubas.bunit.Matcher;

/**
 * {@code ANYTHING_BUT(CONTAINS("draft"))} — inverts another matcher.
 * <p>
 * One function that doubles what every other one can say, which is why it is worth its place when
 * several more obvious matchers are not.
 * <p>
 * Named {@code ANYTHING_BUT} rather than {@code NOT} because {@code NOT} is a core keyword of the
 * language — {@code seal()} refuses a function of that name — and because it pairs with
 * {@link Anything}.
 */
public final class AnythingBut {

    public static final String NAME = "ANYTHING_BUT";

    public Matcher call(Context ctx, Matcher inner) {
        return new Inverted(inner);
    }

    private record Inverted(Matcher inner) implements Matcher {

        @Override
        public boolean matches(Value actual) {
            return !inner.matches(actual);
        }

        @Override
        public String describe() {
            return "anything but " + inner.describe();
        }
    }
}
