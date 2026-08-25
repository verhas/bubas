package javax0.bubas.bunit.commands;

import javax0.bubas.api.Value;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** Comparing and rendering values, for expectations and the messages they fail with. */
final class Values {

    private Values() {
    }

    /**
     * Whether two values are the same as BUBAS sees them. {@code DECIMAL} compares by value rather
     * than by scale, exactly as {@code =} does in the language, so a mock answering {@code 1.50}
     * satisfies an expectation of {@code 1.5}.
     */
    static boolean same(Value expected, Value actual) {
        if (expected == null || actual == null) {
            return expected == actual;
        }
        final var a = expected.as(Object.class);
        final var b = actual.as(Object.class);
        if (a instanceof BigDecimal one && b instanceof BigDecimal other) {
            return one.compareTo(other) == 0;
        }
        return Objects.equals(a, b);
    }

    static boolean same(List<Value> expected, List<Value> actual) {
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

    static String show(Value value) {
        if (value == null) {
            return "nothing";
        }
        final var raw = value.as(Object.class);
        return raw instanceof String text ? '"' + text + '"' : String.valueOf(raw);
    }

    static String show(List<Value> values) {
        return values.stream().map(Values::show).reduce((a, b) -> a + ", " + b).orElse("");
    }
}
