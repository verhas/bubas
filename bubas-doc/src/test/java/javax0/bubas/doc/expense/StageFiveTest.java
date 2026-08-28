package javax0.bubas.doc.expense;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Stage 5: an operation with an opinion, and a rule that decides what to do about it. */
class StageFiveTest {

    private static final BigDecimal LIMIT = new BigDecimal("2000.00");

    private static final Expense.Report ALICE = Expense.claim(1, "Alice",
            Expense.item("travel", "City Taxi", "42.50", true),
            Expense.item("meals", "Cafe Rossi", "18.90", false),
            Expense.item("lodging", "Hotel Meridian", "67.00", true));

    /** One line that a person would look at twice: a large dinner with nothing attached. */
    private static final Expense.Report QUENTIN = Expense.claim(9, "Quentin",
            Expense.item("travel", "City Taxi", "31.00", true),
            Expense.item("meals", "The Ivy", "380.00", false));

    @Test
    void the_same_claim_is_treated_differently_as_the_threshold_moves() throws IOException {
        final var program = Expense.STAGE_5.compile(Runs.source("screened-expense.bu"));

        final var outcomes = List.of(
                Runs.run(program, Runs.args("claim", ALICE, "limit", LIMIT, "flagAt", 8L)),
                Runs.run(program, Runs.args("claim", QUENTIN, "limit", LIMIT, "flagAt", 8L)),
                Runs.run(program, Runs.args("claim", QUENTIN, "limit", LIMIT, "flagAt", 9L)));

        assertThat(outcomes.get(0).answer()).isTrue();
        assertThat(outcomes.get(1).logged()).singleElement().asString().contains("scored 8");
        assertThat(outcomes.get(2).answer())
                .describedAs("raising the threshold lets the same claim through")
                .isTrue();

        Runs.write("stage5-screening.txt", Runs.transcript(outcomes));
    }
}
