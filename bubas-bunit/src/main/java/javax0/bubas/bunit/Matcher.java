package javax0.bubas.bunit;

import javax0.bubas.api.Value;

/**
 * Judges an argument instead of being compared to it.
 * <p>
 * A mock or an expectation ordinarily names the value it wants, which is exact and usually right.
 * When the test is not about the exact value — any order id, a total over the limit, a message
 * mentioning a reason — a matcher says what has to hold rather than what it has to be.
 * <p>
 * It lives in the framework because the framework compares. The matchers themselves are vocabulary:
 * a language ships whichever ones suit it, and an embedder writing its own statements can use the
 * standard ones unchanged, which is why they are a module of their own.
 */
public interface Matcher {

    /** Whether this argument is acceptable. */
    boolean matches(Value actual);

    /**
     * How to name this in a failure — {@code between 1 and 10}, say. A diagnostic that could only
     * report "did not match" would leave the reader to guess what was wanted.
     */
    String describe();
}
