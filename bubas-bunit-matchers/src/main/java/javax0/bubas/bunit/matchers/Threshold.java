package javax0.bubas.bunit.matchers;

import javax0.bubas.api.Value;
import javax0.bubas.bunit.Matcher;

import java.math.BigDecimal;
import java.util.function.IntPredicate;

/**
 * One number compared against a bound.
 * <p>
 * The four comparisons differ only in which results of {@code compareTo} they accept and in the
 * words they use to say so, so they share this rather than repeating it four times.
 *
 * @param word   how the bound reads in a diagnostic — {@code at least}, {@code less than}
 * @param holds  which {@code compareTo} results are acceptable
 */
record Threshold(String word, BigDecimal limit, IntPredicate holds) implements Matcher {

    @Override
    public boolean matches(Value actual) {
        final var number = Numbers.of(actual);
        return number != null && holds.test(number.compareTo(limit));
    }

    @Override
    public String describe() {
        return word + " " + limit.toPlainString();
    }
}
