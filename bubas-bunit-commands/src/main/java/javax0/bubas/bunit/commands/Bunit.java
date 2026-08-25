package javax0.bubas.bunit.commands;

import javax0.bubas.api.Registrar;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The statements a BUBAS unit test is written with.
 * <p>
 * Registered the way any other vocabulary is: {@code builder.install(Bunit::register)}. Because
 * every name a test mentions — a function, a command — is a STRING literal rather than syntax, this
 * vocabulary is the same for every embedder. It reserves only its own words and never competes with
 * the language under test for a reserved word.
 * <p>
 * A test reads as arrange, act, examine, with no phase keywords: the verb of each statement already
 * says which phase it belongs to, so a {@code GIVEN} or {@code THEN} prefix would cost a word per
 * line and carry nothing. Ordering is checked rather than spelled.
 *
 * <pre>
 * PROGRAM ApproveOrderOverLimit
 *     "LOAD_ORDER"  WITH 42   RETURNS "o1"
 *     "ORDER_TOTAL" WITH "o1" RETURNS 1500.00
 *     "APPROVE _" IS MOCKED
 *
 *     ARGUMENT "orderId" IS 42
 *     ARGUMENT "limit"   IS 1000.00
 *
 *     RUN
 *
 *     RESULT IS FALSE
 *     "APPROVE _" WAS NOT CALLED
 *     "LOG_EVENT _, _" WAS CALLED WITH "INFO", "over limit: 1500.00"
 * END.
 * </pre>
 */
public final class Bunit {

    private Bunit() {
    }

    /** Every BUNIT statement, pattern to implementation, in the order a test uses them. */
    public static final Map<String, Class<?>> STATEMENTS = statements();

    private static Map<String, Class<?>> statements() {
        final var map = new LinkedHashMap<String, Class<?>>();
        map.put(Mock.PATTERN, Mock.class);
        map.put(MockWith.PATTERN, MockWith.class);
        map.put(MockWith2.PATTERN, MockWith2.class);
        map.put(MockCommand.PATTERN, MockCommand.class);
        map.put(Sets.PATTERN, Sets.class);
        map.put(Argument.PATTERN, Argument.class);
        map.put(Run.PATTERN, Run.class);
        map.put(ExpectResult.PATTERN, ExpectResult.class);
        map.put(ExpectFailure.PATTERN, ExpectFailure.class);
        map.put(ExpectCalled.PATTERN, ExpectCalled.class);
        map.put(ExpectNotCalled.PATTERN, ExpectNotCalled.class);
        map.put(ExpectCalledWith.PATTERN, ExpectCalledWith.class);
        map.put(ExpectCalledWith2.PATTERN, ExpectCalledWith2.class);
        return Collections.unmodifiableMap(map);
    }

    /** Installs the whole vocabulary: {@code builder.install(Bunit::register)}. */
    public static void register(Registrar registrar) {
        registrar.defineStatements(STATEMENTS);
    }
}
