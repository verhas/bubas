package javax0.bubas.doc.expense;

import javax0.bubas.analyser.BubasLanguage;
import javax0.bubas.bunit.TestResult;
import javax0.bubas.bunit.standard.BunitSuite;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Part 2's BUNIT tests of the expense rules, and the reports the chapters show.
 * <p>
 * Every test here is a real BUNIT file run against a real subject, and every report a chapter
 * quotes is what the runner printed. A chapter claiming a test passes is a chapter whose test
 * passed on this build.
 */
class BunitTest {

    static String bunit(String name) {
        try (var stream = BunitTest.class.getResourceAsStream("/bunit/" + name)) {
            return new String(java.util.Objects.requireNonNull(stream, name).readAllBytes(),
                    StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("cannot read " + name, e);
        }
    }

    static TestResult run(BubasLanguage language, String subject, String test) {
        return BunitSuite.of(language, Runs.source(subject)).run(bunit(test));
    }

    private static TestResult stage1(String test) {
        return run(Expense.STAGE_1, "approve-expense.bu", test);
    }

    @Test
    void a_first_suite_passes_and_its_report_is_written() throws IOException {
        final var results = List.of(
                stage1("under-the-limit-is-approved.bu"),
                stage1("over-the-limit-is-refused.bu"),
                stage1("exactly-at-the-limit-is-approved.bu"));

        assertThat(BunitSuite.allPassed(results))
                .describedAs("%s", BunitSuite.report(results)).isTrue();

        Runs.write("bunit-passing-suite.txt", BunitSuite.report(results));
    }

    @Test
    void a_failing_test_says_what_it_expected() throws IOException {
        final var result = stage1("a-failing-test.bu");

        assertThat(result.passed()).isFalse();
        Runs.write("bunit-failing-test.txt", BunitSuite.report(List.of(result)));
    }

    @Test
    void a_command_that_writes_two_variables_needs_only_the_one_supplying() {
        final var result = run(Expense.STAGE_6, "routed-expense.bu",
                "over-limit-goes-to-a-manager.bu");

        assertThat(result.passed()).describedAs("%s", result.diagnostic()).isTrue();
        assertThat(result.calls())
                .anyMatch(call -> call.startsWith("ROUTE") && call.contains("\"the line manager\""));
    }

    @Test
    void forgetting_to_supply_the_written_variable_is_refused() {
        final var result = run(Expense.STAGE_6, "routed-expense.bu",
                "forgot-to-supply-the-approver.bu");

        assertThat(result.passed()).isFalse();
        assertThat(result.diagnostic()).contains("nothing supplies it on every path").contains("SETS");
        assertThat(result.calls()).describedAs("the subject must not have run").isEmpty();
    }

    /** Chapter 15: two claims of the same type, told apart by the names the test gave them. */
    @Test
    void two_claims_of_one_type_are_told_apart_by_name() throws IOException {
        final var result = stage1("two-claims-told-apart.bu");

        assertThat(result.passed()).describedAs("%s", result.diagnostic()).isTrue();
        Runs.write("bunit-tokens.txt", BunitSuite.report(List.of(result)));
    }

    /** Chapter 16: an expectation that says what matters and leaves the rest alone. */
    @Test
    void matchers_pin_what_matters_and_nothing_else() throws IOException {
        final var result = stage1("matchers.bu");

        assertThat(result.passed()).describedAs("%s", result.diagnostic()).isTrue();
        Runs.write("bunit-matchers.txt", BunitSuite.report(List.of(result)));
    }

    /**
     * Chapter 17: NOTE is left real, so what it writes appears in the log rather than being
     * swallowed by a mock.
     */
    @Test
    void an_operation_left_real_still_does_its_work() throws IOException {
        final var result = run(Expense.STAGE_1, "noted-decision.bu", "notes-are-left-real.bu");

        assertThat(result.passed()).describedAs("%s", result.diagnostic()).isTrue();
        assertThat(result.log()).anyMatch(line -> line.contains("checked against a limit"));
        Runs.write("bunit-partial.txt",
                BunitSuite.report(List.of(result)) + "\nwhat the rule wrote:\n"
                        + String.join("\n", result.log()));
    }

    /** Chapter 19: the model-backed operation, pinned to a known score. */
    @Test
    void the_rule_around_the_model_is_what_gets_tested() throws IOException {
        final var flagged = run(Expense.STAGE_5, "screened-expense.bu",
                "a-flagged-line-is-escalated.bu");
        final var raised = run(Expense.STAGE_5, "screened-expense.bu",
                "the-threshold-is-the-rule.bu");

        assertThat(BunitSuite.allPassed(List.of(flagged, raised)))
                .describedAs("%s", BunitSuite.report(List.of(flagged, raised))).isTrue();

        Runs.write("bunit-model.txt", BunitSuite.report(List.of(flagged, raised)));
    }

    /** Chapter 15: naming the wrong claim is reported as the name it is. */
    @Test
    void naming_the_wrong_claim_says_which_one_it_was() throws IOException {
        final var result = stage1("the-wrong-claim-named.bu");

        assertThat(result.passed()).isFalse();
        Runs.write("bunit-wrong-token.txt", result.diagnostic());
    }

    /** Chapter 18: the report a refused test produces, and the one that passes. */
    @Test
    void the_checker_refusals_are_written_out() throws IOException {
        final var supplied = run(Expense.STAGE_6, "routed-expense.bu",
                "over-limit-goes-to-a-manager.bu");
        final var forgotten = run(Expense.STAGE_6, "routed-expense.bu",
                "forgot-to-supply-the-approver.bu");

        assertThat(supplied.passed()).describedAs("%s", supplied.diagnostic()).isTrue();
        assertThat(forgotten.passed()).isFalse();

        Runs.write("bunit-routing-passes.txt",
                BunitSuite.report(java.util.List.of(supplied))
                        + "\nwhat the rule was given:\n"
                        + String.join("\n", supplied.calls()));
        Runs.write("bunit-unsupplied.txt", forgotten.diagnostic());
    }
}
