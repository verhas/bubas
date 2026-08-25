package javax0.bubas.bunit.standard;

import javax0.bubas.analyser.BubasLanguage;
import javax0.bubas.api.BubasException;
import javax0.bubas.api.Context;
import javax0.bubas.support.Standard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

@DisplayName("running a suite against one subject")
class BunitSuiteTest {

    public static final class Rate {
        public BigDecimal call(Context ctx, String region) {
            throw new IllegalStateException("the real implementation must never run under test");
        }
    }

    private static final BubasLanguage LANGUAGE = BubasLanguage.builder()
            .install(Standard::register)
            .defineFunction("RATE", Rate.class)
            .seal();

    private static final String SUBJECT = """
            PROGRAM TaxOn(amount DECIMAL, region STRING) RETURNS DECIMAL
                RETURN amount * RATE(region)
            END.
            """;

    private static final String PASSES = """
            PROGRAM EuRate
                "RATE" WITH ARGS("EU") RETURNS 0.10
                ARGUMENT "amount" IS 100.00
                ARGUMENT "region" IS "EU"
                RUN
                RESULT IS 10.0000
            END.
            """;

    private static final String FAILS = """
            PROGRAM WrongExpectation
                "RATE" WITH ARGS("EU") RETURNS 0.10
                ARGUMENT "amount" IS 100.00
                ARGUMENT "region" IS "EU"
                RUN
                RESULT IS 99.00
            END.
            """;

    @Test
    void a_suite_reports_every_result_rather_than_stopping_at_the_first_failure() {
        final var results = BunitSuite.of(LANGUAGE, SUBJECT).runAll(List.of(FAILS, PASSES));
        assertThat(results).hasSize(2);
        assertThat(results.getFirst().passed()).isFalse();
        assertThat(results.getLast().passed()).as("%s", results.getLast().diagnostic()).isTrue();
        assertThat(BunitSuite.allPassed(results)).isFalse();
    }

    @Test
    void a_suite_that_all_passes_says_so() {
        final var results = BunitSuite.of(LANGUAGE, SUBJECT).runAll(List.of(PASSES, PASSES));
        assertThat(BunitSuite.allPassed(results)).as("%s", BunitSuite.report(results)).isTrue();
    }

    @Test
    void the_subject_is_compiled_once_and_reused_by_every_test() {
        final var suite = BunitSuite.of(LANGUAGE, SUBJECT);
        assertThat(suite.run(PASSES).passed()).isTrue();
        assertThat(suite.run(PASSES).passed()).isTrue();
    }

    @Test
    void the_report_counts_and_names_what_happened() {
        final var report = BunitSuite.report(
                BunitSuite.of(LANGUAGE, SUBJECT).runAll(List.of(PASSES, FAILS)));
        assertThat(report).startsWith("1/2 passed")
                .contains("PASSED EuRate")
                .contains("FAILED WrongExpectation");
    }

    @Test
    void named_tests_keep_their_order() {
        final var tests = new LinkedHashMap<String, String>();
        tests.put("passes.bu", PASSES);
        tests.put("fails.bu", FAILS);
        final var results = BunitSuite.of(LANGUAGE, SUBJECT).runAll(tests);
        assertThat(results).extracting(r -> r.name())
                .containsExactly("EuRate", "WrongExpectation");
    }

    /** A subject that does not compile is a fault in the subject, not a test outcome. */
    @Test
    void a_subject_that_does_not_compile_throws_rather_than_failing_a_test() {
        assertThat(catchThrowableOfType(BubasException.class,
                () -> BunitSuite.of(LANGUAGE, "PROGRAM Broken\n    WIBBLE\nEND.\n")).getMessage())
                .contains("unknown statement WIBBLE");
    }
}
