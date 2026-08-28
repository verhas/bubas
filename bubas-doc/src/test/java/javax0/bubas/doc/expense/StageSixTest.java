package javax0.bubas.doc.expense;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stage 6: one command that answers twice.
 * <p>
 * Who approves a claim and which budget it lands on are decided together, so they are asked for
 * together. A command earns its place over a function exactly when there is more than one answer;
 * with one answer it would have been {@code approver = FIND_APPROVER(claim)}. See
 * {@code DOCUMENTATION/AUTHORING.md} D19.
 */
class StageSixTest {

    private static final BigDecimal LIMIT = new BigDecimal("200.00");

    private static final Expense.Report ALICE = Expense.claim(1, "Alice",
            Expense.item("travel", "City Taxi", "42.50", true),
            Expense.item("meals", "Cafe Rossi", "18.90", false),
            Expense.item("lodging", "Hotel Meridian", "67.00", true));

    private static final Expense.Report ERIN = Expense.claim(2, "Erin",
            Expense.item("travel", "Rail Europe", "450.00", true));

    private static final Expense.Report FRANK = Expense.claim(3, "Frank",
            Expense.item("lodging", "Grand Hotel", "3000.00", true));

    @Test
    void one_command_supplies_both_the_approver_and_the_budget() throws IOException {
        final var program = Expense.STAGE_6.compile(Runs.source("routed-expense.bu"));

        final var outcomes = List.of(
                Runs.run(program, ALICE, LIMIT),
                Runs.run(program, ERIN, LIMIT),
                Runs.run(program, FRANK, LIMIT));

        assertThat(outcomes.get(0).answer()).isTrue();
        assertThat(outcomes.get(1).logged()).singleElement().asString()
                .contains("the line manager").contains("2500.00");
        assertThat(outcomes.get(2).logged()).singleElement().asString()
                .contains("no budget left").contains("the finance director");

        Runs.write("stage6-routing.txt", Runs.transcript(outcomes));
    }
}
