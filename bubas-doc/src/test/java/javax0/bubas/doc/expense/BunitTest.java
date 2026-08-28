package javax0.bubas.doc.expense;

import javax0.bubas.bunit.standard.BunitSuite;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** Part 2's BUNIT tests of the expense rules. */
class BunitTest {

    private static String bunit(String name) {
        try (var stream = BunitTest.class.getResourceAsStream("/bunit/" + name)) {
            return new String(java.util.Objects.requireNonNull(stream, name).readAllBytes(),
                    StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("cannot read " + name, e);
        }
    }

    @Test
    void a_command_that_writes_two_variables_is_mocked_by_supplying_the_one_that_needs_it() {
        final var result = BunitSuite.of(Expense.STAGE_6, Runs.source("routed-expense.bu"))
                .run(bunit("over-limit-goes-to-a-manager.bu"));

        assertThat(result.passed()).describedAs("%s", result.diagnostic()).isTrue();
        assertThat(result.calls())
                .describedAs("the mock supplied the STRING; the framework supplied a token"
                        + " for the opaque one")
                .anyMatch(call -> call.startsWith("ROUTE") && call.contains("\"the line manager\""));
    }

    /**
     * The other half of the same rule: what the command writes and the mock does not supply is
     * refused before the subject runs, because a test that leaves a variable unset is a
     * test-shaped object that passes.
     */
    @Test
    void forgetting_to_supply_the_written_variable_is_refused() {
        final var result = BunitSuite.of(Expense.STAGE_6, Runs.source("routed-expense.bu"))
                .run(bunit("forgot-to-supply-the-approver.bu"));

        assertThat(result.passed()).isFalse();
        assertThat(result.diagnostic())
                .contains("nothing supplies it on every path")
                .contains("SETS");
        assertThat(result.calls()).describedAs("the subject must not have run").isEmpty();
    }
}
