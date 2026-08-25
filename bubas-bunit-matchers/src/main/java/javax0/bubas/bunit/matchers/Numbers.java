package javax0.bubas.bunit.matchers;

import javax0.bubas.api.BubasType;
import javax0.bubas.api.Value;

import java.math.BigDecimal;

/** What counts as a number to a matcher, in one place so every numeric matcher agrees. */
final class Numbers {

    private Numbers() {
    }

    /**
     * The value as a number, or {@code null} when it is not one.
     * <p>
     * Null rather than an exception: a matcher judges whatever it is handed, so a STRING where a
     * number was expected is a mismatch and not a failure. An expectation that the <em>type</em> is
     * wrong is a different assertion.
     */
    static BigDecimal of(Value value) {
        if (value == null) {
            return null;
        }
        if (value.type() == BubasType.INTEGER) {
            return BigDecimal.valueOf(value.asLong());
        }
        return value.type() == BubasType.DECIMAL ? value.asDecimal() : null;
    }
}
