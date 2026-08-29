package javax0.bubas.doc.expense;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Stage 7: any number of arguments, and arguments of any type. */
class StageSevenTest {

    private static final Expense.Report ALICE = Expense.claim(1, "Alice",
            Expense.item("travel", "City Taxi", "42.50", true),
            Expense.item("meals", "Cafe Rossi", "18.90", false),
            Expense.item("lodging", "Hotel Meridian", "67.00", true));

    private static final Expense.Report ERIN = Expense.claim(2, "Erin",
            Expense.item("travel", "Rail Europe", "450.00", true));

    @Test
    void a_variadic_call_takes_one_or_many_and_a_wildcard_takes_anything() throws IOException {
        final var program = Expense.STAGE_7.compile(Runs.source("told-expense.bu"));

        final var outcomes = List.of(
                Runs.run(program, ALICE, new BigDecimal("200.00")),
                Runs.run(program, ERIN, new BigDecimal("200.00")));

        assertThat(outcomes.get(0).answer()).isTrue();
        assertThat(outcomes.get(0).logged()).anyMatch(l -> l.equals("told the claimant"));
        assertThat(outcomes.get(1).logged()).anyMatch(l -> l.startsWith("told the line manager,"));
        assertThat(outcomes.get(0).logged()).anyMatch(l -> l.contains("(DECIMAL)"));
        assertThat(outcomes.get(0).logged()).anyMatch(l -> l.contains("(BOOLEAN)"));

        Runs.write("stage7-telling.txt", Runs.transcript(outcomes));
    }
}
