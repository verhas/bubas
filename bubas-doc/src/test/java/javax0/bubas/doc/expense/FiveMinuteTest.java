package javax0.bubas.doc.expense;

import javax0.bubas.api.BubasException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Stage 1, and the transcripts the five-minute tutorial shows.
 * <p>
 * The tutorial never quotes an output that some human typed. Asserting a literal here and
 * repeating that literal in the prose would be two copies agreeing by luck; the transcript written
 * by {@link Runs} is one copy, derived. See {@code DOCUMENTATION/AUTHORING.md} D6.
 */
class FiveMinuteTest {

    private static final BigDecimal LIMIT = new BigDecimal("200.00");

    private static final Expense.Report ALICE = Expense.claim(1, "Alice",
            Expense.item("travel", "City Taxi", "42.50", true),
            Expense.item("meals", "Cafe Rossi", "18.90", false),
            Expense.item("lodging", "Hotel Meridian", "67.00", true));

    private static final Expense.Report BOB = Expense.claim(2, "Bob",
            Expense.item("travel", "Conference Ltd", "890.00", true),
            Expense.item("meals", "Le Bernardin", "340.00", true));

    @Test
    void the_tutorial_program_decides_both_claims_and_its_transcript_is_written() throws IOException {
        final var program = Expense.STAGE_1.compile(Runs.source("approve-expense.bu"));

        final var outcomes = List.of(
                Runs.run(program, ALICE, LIMIT),
                Runs.run(program, BOB, LIMIT));

        assertThat(outcomes.get(0).approved()).isTrue();
        assertThat(outcomes.get(1).approved()).isFalse();
        assertThat(outcomes).allSatisfy(outcome -> assertThat(outcome.logged()).hasSize(1));

        Runs.write("five-minutes.txt", Runs.transcript(LIMIT, outcomes));
    }

    /**
     * The rejection the tutorial shows, captured from the compiler rather than transcribed.
     * <p>
     * A quoted diagnostic is the first thing to rot when a message is reworded, and rewording
     * messages is routine.
     */
    @Test
    void a_total_that_was_never_worked_out_is_refused_at_compile_time() throws IOException {
        final var thrown = catchThrowableOfType(BubasException.class,
                () -> Expense.STAGE_1.compile(Runs.source("untotalled-claim.bu")));

        assertThat(thrown).isNotNull();
        assertThat(thrown.getMessage()).contains("'total' is read before it is assigned");

        Runs.write("five-minutes-error.txt", thrown.getDiagnostic());
    }
}
