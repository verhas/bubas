package javax0.bubas.bunit.standard;

import javax0.bubas.analyser.BubasLanguage;
import javax0.bubas.api.BubasType;
import javax0.bubas.api.Context;
import javax0.bubas.api.ExpressionArg;
import javax0.bubas.api.StatementContext;
import javax0.bubas.api.Value;
import javax0.bubas.bunit.BunitRunner;
import javax0.bubas.bunit.Matcher;
import javax0.bubas.bunit.TestResult;
import javax0.bubas.bunit.commands.Bunit;
import javax0.bubas.bunit.matchers.Matchers;
import javax0.bubas.support.Standard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("argument lists and matchers, reaching a mock through real statements")
class MatchersTest {

    // What a matcher *means* is tested in bubas-bunit-matchers, without a language: a matcher is an
    // ordinary object judging an ordinary value. What needs both halves is here — ARGS travelling
    // through actual BUNIT statements into a mock, and the checker counting it on the way.

    public static final class Rate {
        public BigDecimal call(Context ctx, String region, long tier) {
            throw new IllegalStateException("the real implementation must never run under test");
        }
    }

    public static final class Log {
        public void call(StatementContext ctx, ExpressionArg level, ExpressionArg message) {
        }
    }

    private static final BubasLanguage SUBJECT = BubasLanguage.builder()
            .install(Standard::register)
            .defineFunction("RATE", Rate.class)
            .defineStatement("LOG {expression:level}, {expression:message}", Log.class)
            .seal();

    private static final String PROGRAM = """
            PROGRAM Charging(region STRING, tier INTEGER) RETURNS DECIMAL
                DECLARE charge DECIMAL
                charge = RATE(region, tier)
                LOG "INFO", "rate for " + region + " is " + charge
                RETURN charge
            END.
            """;

    private static TestResult run(String test) {
        return BunitRunner.of(BunitLanguage.get(), SUBJECT, PROGRAM).run(test);
    }

    private static String test(String body) {
        return "PROGRAM T\n"
                + "    ARGUMENT \"region\" IS \"EU\"\n"
                + "    ARGUMENT \"tier\" IS 3\n"
                + body
                + "END.\n";
    }

    @Test
    void a_mock_takes_any_number_of_arguments() {
        final var result = run(test("""
                    "RATE" WITH ARGS("EU", 3) RETURNS 0.20
                    RUN
                    RESULT IS 0.20
                """));
        assertThat(result.passed()).as("%s", result.diagnostic()).isTrue();
    }

    @Test
    void a_matcher_may_stand_in_for_an_argument_of_a_mock() {
        final var result = run(test("""
                    "RATE" WITH ARGS("EU", BETWEEN(1, 5)) RETURNS 0.20
                    RUN
                    RESULT IS 0.20
                """));
        assertThat(result.passed()).as("%s", result.diagnostic()).isTrue();
    }

    @Test
    void a_matcher_that_does_not_hold_leaves_the_mock_unmatched() {
        final var result = run(test("""
                    "RATE" WITH ARGS("EU", BETWEEN(10, 20)) RETURNS 0.20
                    RUN
                    RESULT IS 0.20
                """));
        assertThat(result.passed()).isFalse();
    }

    @Test
    void CONTAINS_judges_part_of_a_message() {
        final var result = run(test("""
                    "RATE" WITH ARGS("EU", 3) RETURNS 0.20
                    "LOG _, _" IS MOCKED
                    RUN
                    "LOG _, _" WAS CALLED WITH ARGS("INFO", CONTAINS("rate for EU"))
                """));
        assertThat(result.passed()).as("%s", result.diagnostic()).isTrue();
    }

    @Test
    void a_failed_expectation_describes_the_matcher_rather_than_printing_nothing() {
        final var result = run(test("""
                    "RATE" WITH ARGS("EU", 3) RETURNS 0.20
                    "LOG _, _" IS MOCKED
                    RUN
                    "LOG _, _" WAS CALLED WITH ARGS("INFO", CONTAINS("no such text"))
                """));
        assertThat(result.passed()).isFalse();
        assertThat(result.diagnostic()).contains("containing \"no such text\"");
    }

    @Test
    void a_wrong_argument_count_is_still_caught_through_ARGS() {
        final var result = run(test("""
                    "RATE" WITH ARGS("EU") RETURNS 0.20
                    RUN
                    RESULT IS 0.20
                """));
        assertThat(result.passed()).isFalse();
        assertThat(result.diagnostic()).contains("RATE takes 2 argument(s) but was given 1");
    }

    // ---------------------------------------------------------------- writing your own

    /**
     * A matcher an embedder writes, of a kind the module does not ship. Nothing in the framework
     * had to change to accept it, which is the point of registering matchers like any other
     * function.
     */
    public static final class LongerThan {
        public Matcher call(Context ctx, long length) {
            return new Matcher() {
                @Override
                public boolean matches(Value actual) {
                    return actual.type() == BubasType.STRING && actual.asString().length() > length;
                }

                @Override
                public String describe() {
                    return "longer than " + length + " characters";
                }
            };
        }
    }

    @Test
    void an_embedder_can_add_a_matcher_of_their_own() {
        final var language = BubasLanguage.builder()
                .install(Standard::register)
                .install(Matchers::register)
                .install(Bunit::register)
                .defineFunction("LONGER_THAN", LongerThan.class)
                .seal();
        final var result = BunitRunner.of(language, SUBJECT, PROGRAM).run(test("""
                    "RATE" WITH ARGS("EU", 3) RETURNS 0.20
                    "LOG _, _" IS MOCKED
                    RUN
                    "LOG _, _" WAS CALLED WITH ARGS(STARTS_WITH("IN"), LONGER_THAN(5))
                """));
        assertThat(result.passed()).as("%s", result.diagnostic()).isTrue();
    }
}
