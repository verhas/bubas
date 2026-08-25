package javax0.bubas.bunit;

import javax0.bubas.api.Value;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Comparing an expected argument with an actual one, and saying so afterwards.
 * <p>
 * One place, because the recorder does this while the subject runs and the expectation statements
 * do it afterwards. Two implementations would eventually disagree about a {@link Matcher} or about
 * the scale of a DECIMAL, and the disagreement would look like a bug in whichever half the reader
 * was not looking at.
 */
public final class Matching {

    private Matching() {
    }

    /**
     * A {@link Matcher} judges; anything else is compared. DECIMAL compares by value rather than by
     * scale, exactly as {@code =} does in the language, so a mock answering {@code 1.50} satisfies
     * an expectation of {@code 1.5}.
     */
    public static boolean same(Value expected, Value actual) {
        if (expected == null || actual == null) {
            return expected == actual;
        }
        final var wanted = expected.as(Object.class);
        if (wanted instanceof Matcher matcher) {
            return matcher.matches(actual);
        }
        final var got = actual.as(Object.class);
        if (wanted instanceof BigDecimal one && got instanceof BigDecimal other) {
            return one.compareTo(other) == 0;
        }
        return Objects.equals(wanted, got);
    }

    public static boolean same(List<Value> expected, List<Value> actual) {
        if (expected.size() != actual.size()) {
            return false;
        }
        for (int i = 0; i < expected.size(); i++) {
            if (!same(expected.get(i), actual.get(i))) {
                return false;
            }
        }
        return true;
    }

    /** How a value reads in a diagnostic. A matcher describes itself; a string keeps its quotes. */
    public static String show(Value value) {
        if (value == null) {
            return "nothing";
        }
        final var raw = value.as(Object.class);
        if (raw instanceof Matcher matcher) {
            return matcher.describe();
        }
        return raw instanceof String text ? '"' + text + '"' : String.valueOf(raw);
    }

    public static String show(List<Value> values) {
        return values.stream().map(Matching::show).reduce((a, b) -> a + ", " + b).orElse("");
    }
}
