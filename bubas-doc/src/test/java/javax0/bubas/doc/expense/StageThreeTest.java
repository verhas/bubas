package javax0.bubas.doc.expense;

import javax0.bubas.api.BubasException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Stage 3: the claim stops being a single number and becomes a list of lines.
 * <p>
 * Every claim below drives one branch of the rule to its conclusion. If a branch stops being
 * reachable the assertion fails here, which is the only thing preventing a document from
 * describing behaviour the program no longer has.
 */
class StageThreeTest {

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
    void every_branch_of_the_itemised_rule_is_reached() throws IOException {
        final var program = Expense.STAGE_3.compile(Runs.source("itemised-expense.bu"));

        final var outcomes = List.of(
                Runs.run(program, ALICE, LIMIT),
                Runs.run(program, DAVE, LIMIT),
                Runs.run(program, CAROL, LIMIT),
                Runs.run(program, ERIN, LIMIT),
                Runs.run(program, FRANK, LIMIT));

        assertThat(outcomes.get(0).answer()).isTrue();
        assertThat(outcomes.get(1).logged()).singleElement().asString().contains("no receipt");
        assertThat(outcomes.get(2).logged()).singleElement().asString().contains("meals came to");
        assertThat(outcomes.get(3).logged()).singleElement().asString().contains("over the");
        assertThat(outcomes.get(4).logged()).singleElement().asString().contains("business case");
        assertThat(outcomes).filteredOn(Runs.Outcome::answer).hasSize(1);

        Runs.write("stage3-decisions.txt", Runs.transcript(outcomes));
    }

    /**
     * Proves the stages are really stages: the earlier language genuinely cannot compile the later
     * program. Without this the staging could quietly become decorative.
     */
    @Test
    void a_stage_three_operation_does_not_exist_in_the_stage_two_language() {
        assertThat(catchThrowableOfType(BubasException.class,
                () -> Expense.STAGE_2.compile(Runs.source("itemised-expense.bu")))).isNotNull();
    }

    /**
     * Two opaque types are two types. The message names both, and names the parameter it wanted it
     * for, which is why the Java parameter is called {@code line} rather than {@code x}.
     */
    @Test
    void one_opaque_type_cannot_stand_in_for_another() throws IOException {
        final var thrown = catchThrowableOfType(BubasException.class,
                () -> Expense.STAGE_3.compile(Runs.source("mixed-up-types.bu")));

        assertThat(thrown).isNotNull();
        assertThat(thrown.getMessage()).contains("takes Item for 'line', but was given Report");

        Runs.write("stage3-wrong-type.txt", thrown.getDiagnostic());
    }

    /** Chapter 7: leaving a loop early, and the claim that has nothing to leave early for. */
    @Test
    void a_loop_can_stop_as_soon_as_it_has_its_answer() throws IOException {
        final var program = Expense.STAGE_3.compile(Runs.source("first-big-item.bu"));

        final var outcomes = List.of(
                Runs.run(program, ALICE, LIMIT),
                Runs.run(program, ERIN, LIMIT));

        assertThat(outcomes.get(0).answer()).isTrue();
        assertThat(outcomes.get(1).logged()).singleElement().asString().contains("line 1");

        Runs.write("stage3-exit-for.txt", Runs.transcript(outcomes));
    }

    /** Chapter 7: a DO WHILE, whose condition watches two things at once. */
    @Test
    void a_do_loop_tests_its_condition_before_each_pass() throws IOException {
        final var program = Expense.STAGE_3.compile(Runs.source("running-total.bu"));

        final var outcomes = List.of(
                Runs.run(program, ALICE, LIMIT),
                Runs.run(program, CAROL, new BigDecimal("50.00")));

        assertThat(outcomes.get(0).answer()).isTrue();
        assertThat(outcomes.get(1).logged()).singleElement().asString().contains("passed the limit");

        Runs.write("stage3-do-while.txt", Runs.transcript(outcomes));
    }

    /**
     * Chapter 8's family of refusals, each captured from the compiler.
     * <p>
     * {@code unused-variable.bu} is deliberately absent from this list. The rule works, but the
     * diagnostic reports the declaration's line number while attaching the source text of line 1,
     * and a book should not print a diagnostic whose two halves disagree. The chapter covers that
     * rule in prose until the message is fixed.
     */
    @Test
    void the_compiler_refuses_a_family_of_mistakes() throws IOException {
        record Refusal(String program, String output, String expected) {
        }
        final var refusals = List.of(
                new Refusal("final-reassigned.bu", "stage3-final.txt",
                        "is final and cannot be changed"),
                new Refusal("missing-return.bu", "stage3-missing-return.txt",
                        "can reach its end without returning a value"),
                new Refusal("loop-variable-assigned.bu", "stage3-loop-variable.txt",
                        "cannot be assigned inside it"),
                new Refusal("top-tested-loop.bu", "stage3-top-tested.txt",
                        "'lastMerchant' is read before it is assigned"));

        for (final var refusal : refusals) {
            final var thrown = catchThrowableOfType(BubasException.class,
                    () -> Expense.STAGE_3.compile(Runs.source(refusal.program())));
            assertThat(thrown).describedAs("%s should not compile", refusal.program()).isNotNull();
            assertThat(thrown.getMessage()).contains(refusal.expected());
            assertThat(thrown.getSourceLine())
                    .describedAs("%s: the reported line and its text must agree", refusal.program())
                    .isEqualTo(Runs.source(refusal.program()).split("\n")[thrown.getLine() - 1]);
            Runs.write(refusal.output(), thrown.getDiagnostic());
        }
    }

    /**
     * The same loop, tested at the bottom instead of the top, compiles — because a body that always
     * runs once can satisfy the compiler that the variable was set.
     */
    @Test
    void a_bottom_tested_loop_satisfies_definite_assignment() {
        assertThat(catchThrowableOfType(BubasException.class,
                () -> Expense.STAGE_3.compile(Runs.source("bottom-tested-loop.bu"))))
                .describedAs("the bottom-tested form should compile")
                .isNull();
    }
}
