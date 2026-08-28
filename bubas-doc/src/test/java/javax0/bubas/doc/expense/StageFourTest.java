package javax0.bubas.doc.expense;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stage 4: the same rule written twice — once by hand with an array, once by asking an operation.
 * <p>
 * Both are run against the same claims and must reach the same decisions. That equivalence is the
 * whole of chapter 9's argument, so it is asserted rather than claimed.
 */
class StageFourTest {

    private static final BigDecimal MEAL_CAP = new BigDecimal("60.00");
    private static final BigDecimal TRAVEL_CAP = new BigDecimal("500.00");

    private static final Expense.Report ALICE = Expense.claim(1, "Alice",
            Expense.item("travel", "City Taxi", "42.50", true),
            Expense.item("meals", "Cafe Rossi", "18.90", false),
            Expense.item("lodging", "Hotel Meridian", "67.00", true));

    private static final Expense.Report CAROL = Expense.claim(4, "Carol",
            Expense.item("meals", "Osteria", "40.00", true),
            Expense.item("meals", "Trattoria", "35.00", true));

    private static final Expense.Report ERIN = Expense.claim(2, "Erin",
            Expense.item("travel", "Rail Europe", "450.00", true));

    private static List<Runs.Outcome> decide(String program, javax0.bubas.analyser.BubasLanguage
            language) {
        final var compiled = language.compile(Runs.source(program));
        return List.of(
                Runs.run(compiled, Runs.args("claim", ALICE, "mealCap", MEAL_CAP,
                        "travelCap", TRAVEL_CAP)),
                Runs.run(compiled, Runs.args("claim", CAROL, "mealCap", MEAL_CAP,
                        "travelCap", TRAVEL_CAP)),
                Runs.run(compiled, Runs.args("claim", ERIN, "mealCap", MEAL_CAP,
                        "travelCap", TRAVEL_CAP)));
    }

    @Test
    void the_hand_written_and_the_asked_version_decide_alike() throws IOException {
        final var byHand = decide("category-array.bu", Expense.STAGE_3);
        final var byAsking = decide("category-caps.bu", Expense.STAGE_4);

        assertThat(byHand).extracting(Runs.Outcome::answer)
                .isEqualTo(byAsking.stream().map(Runs.Outcome::answer).toList());
        assertThat(byHand).extracting(Runs.Outcome::logged)
                .isEqualTo(byAsking.stream().map(Runs.Outcome::logged).toList());
        assertThat(byHand.get(0).answer()).isTrue();
        assertThat(byHand.get(1).logged()).singleElement().asString().contains("meals came to");

        Runs.write("stage4-by-hand.txt", Runs.transcript(byHand));
        Runs.write("stage4-by-asking.txt", Runs.transcript(byAsking));
    }
}
