package javax0.bubas.bunit.matchers;

import javax0.bubas.api.Registrar;
import javax0.bubas.bunit.Matcher;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Argument lists and matchers, registered the way any other vocabulary is:
 * {@code builder.install(Matchers::register)}.
 * <p>
 * <pre>
 * "RATE" WITH ARGS("EU") RETURNS 0.10
 * "LOG_EVENT _, _" WAS CALLED WITH ARGS("INFO", CONTAINS("over limit"))
 * "APPROVE _" WAS CALLED WITH ARGS(BETWEEN(100, 500), ANYTHING())
 * </pre>
 *
 * <table border="1">
 *   <caption>the matchers</caption>
 *   <tr><td>{@code ANYTHING()}</td><td>this argument is not what the test is about</td></tr>
 *   <tr><td>{@code ANYTHING_BUT(m)}</td><td>inverts another matcher</td></tr>
 *   <tr><td>{@code GREATER_THAN(n)}</td><td>strictly above</td></tr>
 *   <tr><td>{@code AT_LEAST(n)}</td><td>at or above</td></tr>
 *   <tr><td>{@code LESS_THAN(n)}</td><td>strictly below</td></tr>
 *   <tr><td>{@code AT_MOST(n)}</td><td>at or below</td></tr>
 *   <tr><td>{@code BETWEEN(low, high)}</td><td>in range, both ends included</td></tr>
 *   <tr><td>{@code CONTAINS(s)}</td><td>a STRING with that text somewhere in it</td></tr>
 *   <tr><td>{@code STARTS_WITH(s)}</td><td>a STRING beginning with it</td></tr>
 *   <tr><td>{@code ENDS_WITH(s)}</td><td>a STRING ending with it</td></tr>
 *   <tr><td>{@code MATCHES(regex)}</td><td>a STRING the whole of which fits the expression</td></tr>
 * </table>
 *
 * Present tense throughout: a matcher says what always holds of the argument, not what is happening
 * to it, and the shorter word reads better in a line an author scans rather than parses.
 * <p>
 * Numeric bounds are DECIMAL, so an INTEGER widens into them and one matcher covers both types.
 * A matcher judges whatever it is handed: a {@code BETWEEN} given a STRING does not match, and does
 * not fail — asserting that the type is wrong is a different assertion.
 * <p>
 * An embedder needing something not here writes it against {@link Matcher} and registers it
 * alongside these; nothing in the framework changes, and the tests of this module show it being
 * done.
 */
public final class Matchers {

    private Matchers() {
    }

    /** The opaque types this vocabulary introduces, and what a test may build with them. */
    public static final Map<String, Class<?>> TYPES = Map.of(
            "Arguments", Arguments.class,
            "Matcher", Matcher.class);

    public static final Map<String, Class<?>> FUNCTIONS = functions();

    private static Map<String, Class<?>> functions() {
        final var map = new LinkedHashMap<String, Class<?>>();
        map.put(Args.NAME, Args.class);
        map.put(Anything.NAME, Anything.class);
        map.put(AnythingBut.NAME, AnythingBut.class);
        map.put(GreaterThan.NAME, GreaterThan.class);
        map.put(AtLeast.NAME, AtLeast.class);
        map.put(LessThan.NAME, LessThan.class);
        map.put(AtMost.NAME, AtMost.class);
        map.put(Between.NAME, Between.class);
        map.put(Contains.NAME, Contains.class);
        map.put(StartsWith.NAME, StartsWith.class);
        map.put(EndsWith.NAME, EndsWith.class);
        map.put(Matches.NAME, Matches.class);
        return Collections.unmodifiableMap(map);
    }

    public static void register(Registrar registrar) {
        registrar.defineOpaqueTypes(TYPES).defineFunctions(FUNCTIONS);
    }
}
