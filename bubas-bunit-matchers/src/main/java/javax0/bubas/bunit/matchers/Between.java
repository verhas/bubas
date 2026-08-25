package javax0.bubas.bunit.matchers;

import javax0.bubas.api.Context;
import javax0.bubas.api.Value;
import javax0.bubas.bunit.Matcher;

import java.math.BigDecimal;

/**
 * {@code BETWEEN(100, 500)} — a number in that range, both ends included.
 * <p>
 * Bounds are DECIMAL, so an INTEGER widens into them and one matcher covers both numeric types.
 * Inclusive because a business rule that says "between" almost always means it; a test needing the
 * open range says {@code GREATER_THAN} and {@code LESS_THAN} instead.
 */
public final class Between {

    public static final String NAME = "BETWEEN";

    public Matcher call(Context ctx, BigDecimal low, BigDecimal high) {
        return new Range(low, high);
    }

    private record Range(BigDecimal low, BigDecimal high) implements Matcher {

        @Override
        public boolean matches(Value actual) {
            final var number = Numbers.of(actual);
            return number != null
                    && low.compareTo(number) <= 0
                    && high.compareTo(number) >= 0;
        }

        @Override
        public String describe() {
            return "between " + low.toPlainString() + " and " + high.toPlainString();
        }
    }
}
