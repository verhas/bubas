package javax0.bubas.doc.expense;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Stage 2: a third outcome, so a rule need not choose between yes and no. */
class StageTwoTest {

    private static final BigDecimal LIMIT = new BigDecimal("200.00");

    /** Under every threshold. The 18.90 lunch is below the receipt floor, so it needs none. */
    private static final Expense.Report ALICE = Expense.claim(1, "Alice",
            Expense.item("travel", "City Taxi", "42.50", true),
            Expense.item("meals", "Cafe Rossi", "18.90", false),
            Expense.item("lodging", "Hotel Meridian", "67.00", true));

    /** Over the personal limit but well under the ceiling: a manager decides. */
    private static final Expense.Report ERIN = Expense.claim(2, "Erin",
            Expense.item("travel", "Rail Europe", "450.00", true));

    /** Over the ceiling: no manager can wave this through. */
    private static final Expense.Report FRANK = Expense.claim(3, "Frank",
            Expense.item("lodging", "Grand Hotel", "1200.00", true));

    /** Small in total, but the meals alone break the cap. */
    private static final Expense.Report CAROL = Expense.claim(4, "Carol",
            Expense.item("meals", "Osteria", "40.00", true),
            Expense.item("meals", "Trattoria", "35.00", true));

    /** A taxi above the receipt floor with nothing attached. */
    private static final Expense.Report DAVE = Expense.claim(5, "Dave",
            Expense.item("travel", "City Taxi", "42.50", false));

    @Test
    void the_middle_ground_goes_to_a_manager() throws IOException {
        final var program = Expense.STAGE_2.compile(Runs.source("escalating-expense.bu"));

        final var outcomes = List.of(
                Runs.run(program, ALICE, LIMIT),
                Runs.run(program, ERIN, LIMIT),
                Runs.run(program, FRANK, LIMIT));

        assertThat(outcomes.get(0).answer()).isTrue();
        assertThat(outcomes.get(1).logged()).singleElement().asString().contains("escalated");
        assertThat(outcomes.get(2).logged()).singleElement().asString().contains("rejected");

        Runs.write("stage2-decisions.txt", Runs.transcript(outcomes));
    }

    /**
     * Chapter 6: every branch of an IF / ELSEIF / ELSE chain, and the boolean operators that pick
     * between them. The same claim at the same limit goes two different ways depending on one
     * BOOLEAN, which is the cheapest possible demonstration that the order of the tests is policy.
     */
    @Test
    void every_branch_of_the_chain_is_reachable() throws IOException {
        final var program = Expense.STAGE_2.compile(Runs.source("urgent-expense.bu"));

        final var outcomes = List.of(
                Runs.run(program, Runs.args("claim", ALICE, "limit", LIMIT, "urgent", false)),
                Runs.run(program, Runs.args("claim", ERIN, "limit", LIMIT, "urgent", false)),
                Runs.run(program, Runs.args("claim", ERIN, "limit", LIMIT, "urgent", true)),
                Runs.run(program, Runs.args("claim", FRANK, "limit", LIMIT, "urgent", true)));

        assertThat(outcomes.get(0).answer()).isTrue();
        assertThat(outcomes.get(1).logged()).singleElement().asString().contains("escalated");
        assertThat(outcomes.get(2).answer()).isTrue();
        assertThat(outcomes.get(2).logged()).first().asString().contains("urgent");
        assertThat(outcomes.get(3).logged()).singleElement().asString().contains("rejected");

        Runs.write("stage2-branches.txt", Runs.transcript(outcomes));
    }
}
