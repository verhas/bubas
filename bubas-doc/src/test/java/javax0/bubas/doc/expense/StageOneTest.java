package javax0.bubas.doc.expense;

import javax0.bubas.api.BubasException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Stage 1, and the transcripts every document showing stage 1 includes.
 * <p>
 * The tutorial never quotes an output that some human typed. Asserting a literal here and
 * repeating that literal in the prose would be two copies agreeing by luck; the transcript written
 * by {@link Runs} is one copy, derived. See {@code DOCUMENTATION/AUTHORING.md} D6.
 */
class StageOneTest {

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

        assertThat(outcomes.get(0).answer()).isTrue();
        assertThat(outcomes.get(1).answer()).isFalse();
        assertThat(outcomes).allSatisfy(outcome -> assertThat(outcome.logged()).hasSize(1));

        Runs.write("stage1-decisions.txt", Runs.transcript(outcomes));
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

        Runs.write("stage1-untotalled.txt", thrown.getDiagnostic());
    }

    /**
     * A switch the program sets itself is not a switch.
     * <p>
     * Chapter 8 uses this pair. The rejection is the interesting half: nothing here is written as a
     * constant, and the compiler still answers the condition, because it followed the value.
     */
    @Test
    void a_switch_the_program_sets_itself_is_refused() throws IOException {
        final var thrown = catchThrowableOfType(BubasException.class,
                () -> Expense.STAGE_1.compile(Runs.source("switched-expense.bu")));

        assertThat(thrown).isNotNull();
        assertThat(thrown.getMessage()).contains("always TRUE");

        Runs.write("stage1-switched.txt", thrown.getDiagnostic());
    }

    /** The same rule with the switch where it belongs: one program, two answers. */
    @Test
    void the_same_switch_as_a_parameter_compiles_and_decides_both_ways() throws IOException {
        final var program = Expense.STAGE_1.compile(Runs.source("switched-expense-fixed.bu"));

        final var outcomes = List.of(
                Runs.run(program, Runs.args("claim", ALICE, "limit", LIMIT, "strict", false)),
                Runs.run(program, Runs.args("claim", ALICE, "limit", LIMIT, "strict", true)));

        assertThat(outcomes.get(0).answer()).isTrue();
        assertThat(outcomes.get(1).answer()).isFalse();

        Runs.write("stage1-switched-parameter.txt", Runs.transcript(outcomes));
    }

    /**
     * One compiled program, one claim, two limits, two answers.
     * <p>
     * Chapter 2 uses this to show that a parameter is what makes a program reusable: nothing is
     * recompiled between these two runs.
     */
    @Test
    void the_same_program_decides_differently_when_the_limit_changes() throws IOException {
        final var program = Expense.STAGE_1.compile(Runs.source("approve-expense.bu"));

        final var outcomes = List.of(
                Runs.run(program, ALICE, new BigDecimal("200.00")),
                Runs.run(program, ALICE, new BigDecimal("100.00")));

        assertThat(outcomes.get(0).answer()).isTrue();
        assertThat(outcomes.get(1).answer()).isFalse();

        Runs.write("stage1-limit-effect.txt", Runs.transcript(outcomes));
    }

    /**
     * Chapter 3's demonstration that DECIMAL is exact and keeps its scale, and that division does
     * not: it goes through the language's MathContext.
     * <p>
     * The amounts are deliberately awkward. Nobody files a thirty-cent claim; the point is that a
     * language handling money has to add these two numbers and get exactly the third.
     */
    @Test
    void decimal_addition_is_exact_and_division_is_not() throws IOException {
        final var program = Expense.STAGE_1.compile(Runs.source("per-diem.bu"));

        final var pence = Expense.claim(6, "Nadia",
                Expense.item("meals", "Kiosk", "0.10", false),
                Expense.item("meals", "Kiosk", "0.20", false));
        final var scaled = Expense.claim(7, "Omar",
                Expense.item("travel", "Bus", "10.50", false));
        final var thirds = Expense.claim(8, "Priya",
                Expense.item("lodging", "Guesthouse", "100.00", true));

        final var outcomes = List.of(
                Runs.run(program, Runs.args("claim", pence,
                        "dailyCap", new BigDecimal("20.00"), "days", 1L)),
                Runs.run(program, Runs.args("claim", scaled,
                        "dailyCap", new BigDecimal("20.00"), "days", 1L)),
                Runs.run(program, Runs.args("claim", thirds,
                        "dailyCap", new BigDecimal("30.00"), "days", 3L)));

        assertThat(outcomes.get(0).logged()).singleElement().asString().endsWith("for 0.30");
        assertThat(outcomes.get(1).logged()).singleElement().asString().endsWith("for 10.50");
        assertThat(outcomes.get(2).logged()).singleElement().asString().contains("33.33");

        Runs.write("stage1-values.txt", Runs.transcript(outcomes));
    }

    /**
     * Chapter 4's demonstration: an operation that answers nothing is written as a statement, and
     * the brackets are optional. Both calls below are the same operation.
     */
    @Test
    void an_operation_that_answers_nothing_is_written_as_a_statement() throws IOException {
        final var program = Expense.STAGE_1.compile(Runs.source("noted-decision.bu"));

        final var outcomes = List.of(
                Runs.run(program, ALICE, new BigDecimal("200.00")),
                Runs.run(program, BOB, new BigDecimal("200.00")));

        assertThat(outcomes.get(0).logged()).hasSize(2);
        assertThat(outcomes.get(1).logged()).hasSize(3);
        assertThat(outcomes.get(1).logged().get(1)).isEqualTo("over by 1030.00");

        Runs.write("stage1-notes.txt", Runs.transcript(outcomes));
    }

    /**
     * Chapter 4: an operation that answers cannot be used as if it did not. The result of a
     * question has to go somewhere, so a rule cannot ask and then quietly ignore the answer.
     */
    @Test
    void an_answer_cannot_be_thrown_away() throws IOException {
        final var thrown = catchThrowableOfType(BubasException.class,
                () -> Expense.STAGE_1.compile(Runs.source("discarded-answer.bu")));

        assertThat(thrown).isNotNull();
        Runs.write("stage1-discarded.txt", thrown.getDiagnostic());
    }

    /**
     * Chapter 5: the three things a program may not do to a value it cannot open.
     * <p>
     * Reaching inside one is the interesting failure. There is no member access in BUBAS to
     * complain about, so the line simply matches no statement at all, and the message says so
     * rather than mentioning dots. The chapter shows that rather than hiding it.
     */
    @Test
    void an_opaque_value_cannot_be_opened_rendered_or_compared() throws IOException {
        record Case(String program, String output, String expected) {
        }
        final var cases = List.of(
                new Case("reaching-inside.bu", "stage1-reaching-inside.txt", "unknown statement"),
                new Case("rendering-a-claim.bu", "stage1-rendering.txt", "has no text form"),
                new Case("comparing-claims.bu", "stage1-comparing.txt", "cannot be compared"));

        for (final var each : cases) {
            final var thrown = catchThrowableOfType(BubasException.class,
                    () -> Expense.STAGE_1.compile(Runs.source(each.program())));
            assertThat(thrown)
                    .describedAs("%s should not compile", each.program())
                    .isNotNull();
            assertThat(thrown.getMessage()).contains(each.expected());
            Runs.write(each.output(), thrown.getDiagnostic());
        }
    }
}
