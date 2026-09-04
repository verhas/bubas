package javax0.bubas.doc.expense;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stage 9: an array holding something the domain is plural about.
 * <p>
 * Chapter 9 argues that wanting an array usually means a rule has left business logic. This is the
 * exception it names: the collection came <em>from</em> the domain rather than being assembled by
 * the rule, and looping over it is not a smell.
 */
class StageNineTest {

    private static final BigDecimal LIMIT = new BigDecimal("200.00");

    private static final Expense.Report SMALL = Expense.claim(1, "Alice",
            Expense.item("travel", "City Taxi", "42.50", true));

    private static final Expense.Report LARGE = Expense.claim(2, "Bob",
            Expense.item("lodging", "Grand Hotel", "3000.00", true));

    @Test
    void a_rule_may_walk_a_list_the_domain_handed_it() throws IOException {
        final var program = Expense.STAGE_9.compile(Runs.source("notify-approvers.bu"));

        final var outcomes = List.of(
                Runs.run(program, SMALL, LIMIT),
                Runs.run(program, LARGE, LIMIT));

        assertThat(outcomes.get(0).answer()).isTrue();
        assertThat(outcomes.get(1).logged())
                .describedAs("three approvers, then the escalation")
                .hasSize(4);
        assertThat(outcomes.get(1).logged().get(0)).isEqualTo("told the line manager");
        assertThat(outcomes.get(1).logged()).last().asString().contains("3 signatures");

        Runs.write("stage9-approvers.txt", Runs.transcript(outcomes));
    }

    /**
     * Chapter 28's rounding snippet, kept honest: the policy is a property of the language, so the
     * same rule compiled against a different one divides differently.
     */
    @Test
    void the_rounding_policy_travels_with_the_language() {
        final var rounded = Wiring.roundedToTheCent();

        assertThat(rounded.mathContext().getPrecision()).isEqualTo(16);
        assertThat(Expense.STAGE_9.mathContext().getPrecision())
                .as("the book's own language is left at the default")
                .isEqualTo(java.math.MathContext.DECIMAL128.getPrecision());
    }
}
