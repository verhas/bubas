package javax0.bubas.doc.expense;

import javax0.bubas.api.BubasException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Stages 2 and 3, and the transcripts the fifteen-minute tutorial shows.
 * <p>
 * Every claim below exists to drive one branch of the rule to its conclusion. If a branch stops
 * being reachable the assertion fails here, which is the only way the tutorial can be prevented
 * from describing behaviour the program no longer has.
 */
class FifteenMinuteTest {

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
    void stage_two_sends_the_middle_ground_to_a_manager() throws IOException {
        final var program = Expense.STAGE_2.compile(Runs.source("escalating-expense.bu"));

        final var outcomes = List.of(
                Runs.run(program, ALICE, LIMIT),
                Runs.run(program, ERIN, LIMIT),
                Runs.run(program, FRANK, LIMIT));

        assertThat(outcomes.get(0).approved()).isTrue();
        assertThat(outcomes.get(1).logged()).singleElement().asString().contains("escalated");
        assertThat(outcomes.get(2).logged()).singleElement().asString().contains("rejected");

        Runs.write("fifteen-minutes-escalation.txt", Runs.transcript(LIMIT, outcomes));
    }

    @Test
    void stage_three_reaches_every_branch_of_the_itemised_rule() throws IOException {
        final var program = Expense.STAGE_3.compile(Runs.source("itemised-expense.bu"));

        final var outcomes = List.of(
                Runs.run(program, ALICE, LIMIT),
                Runs.run(program, DAVE, LIMIT),
                Runs.run(program, CAROL, LIMIT),
                Runs.run(program, ERIN, LIMIT),
                Runs.run(program, FRANK, LIMIT));

        assertThat(outcomes.get(0).approved()).isTrue();
        assertThat(outcomes.get(1).logged()).singleElement().asString().contains("no receipt");
        assertThat(outcomes.get(2).logged()).singleElement().asString().contains("meals came to");
        assertThat(outcomes.get(3).logged()).singleElement().asString().contains("over the");
        assertThat(outcomes.get(4).logged()).singleElement().asString().contains("business case");
        assertThat(outcomes).filteredOn(Runs.Outcome::approved).hasSize(1);

        Runs.write("fifteen-minutes-itemised.txt", Runs.transcript(LIMIT, outcomes));
    }

    /**
     * Proves the stages are really stages: the language the earlier tutorial shows genuinely
     * cannot compile the later tutorial's program. Without this the staging could quietly become
     * decorative, with one language behind both documents.
     */
    @Test
    void a_stage_three_operation_does_not_exist_in_the_stage_two_language() {
        assertThat(catchThrowableOfType(BubasException.class,
                () -> Expense.STAGE_2.compile(Runs.source("itemised-expense.bu")))).isNotNull();
    }

    /**
     * Two opaque types are two types. The message names both, and names the parameter it was
     * given for, which is why the Java parameter is called {@code line} rather than {@code x}.
     */
    @Test
    void one_opaque_type_cannot_stand_in_for_another() throws IOException {
        final var thrown = catchThrowableOfType(BubasException.class,
                () -> Expense.STAGE_3.compile(Runs.source("mixed-up-types.bu")));

        assertThat(thrown).isNotNull();
        assertThat(thrown.getMessage()).contains("takes Item for 'line', but was given Report");

        Runs.write("fifteen-minutes-error.txt", thrown.getDiagnostic());
    }
}
