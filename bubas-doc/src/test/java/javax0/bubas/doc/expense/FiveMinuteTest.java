package javax0.bubas.doc.expense;

import javax0.bubas.api.BubasException;
import javax0.bubas.runtime.Interpreter;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Runs the program the five-minute tutorial shows, and writes what it actually printed to
 * {@code target/doc-outputs/}, which the tutorial includes.
 * <p>
 * The tutorial therefore never quotes an output that some human typed. Asserting a literal here
 * and repeating that literal in the prose would be two copies agreeing by luck; this is one copy,
 * derived. See {@code DOCUMENTATION/AUTHORING.md} D6.
 */
class FiveMinuteTest {

    private static final Path OUTPUTS = Path.of("target", "doc-outputs");

    private static final BigDecimal LIMIT = new BigDecimal("200.00");

    private static String source(String name) {
        try (var stream = FiveMinuteTest.class.getResourceAsStream("/programs/" + name)) {
            return new String(Objects.requireNonNull(stream, name).readAllBytes(),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + name, e);
        }
    }

    /** One run, with everything the program logged collected in order. */
    private record Run(boolean approved, List<String> logged) {
    }

    private static Run run(javax0.bubas.analyser.BubasProgram program, Expense.Report claim) {
        final var logged = new ArrayList<String>();
        final var result = Interpreter.of(program)
                .argument("claim", claim)
                .argument("limit", LIMIT)
                .logger((level, message) -> logged.add(message))
                .run();
        return new Run(result.asBoolean(), List.copyOf(logged));
    }

    @Test
    void the_tutorial_program_decides_both_claims_and_its_transcript_is_written() throws IOException {
        final var program = Expense.STAGE_1.compile(source("approve-expense.bu"));

        final var aliceClaim = Expense.claim(1, "Alice",
                Expense.item("travel", "City Taxi", "42.50", true),
                Expense.item("meals", "Cafe Rossi", "18.90", false),
                Expense.item("lodging", "Hotel Meridian", "67.00", true));
        final var bobClaim = Expense.claim(2, "Bob",
                Expense.item("travel", "Conference Ltd", "890.00", true),
                Expense.item("meals", "Le Bernardin", "340.00", true));

        final var alice = run(program, aliceClaim);
        final var bob = run(program, bobClaim);

        assertThat(alice.approved()).isTrue();
        assertThat(bob.approved()).isFalse();
        assertThat(alice.logged()).hasSize(1);
        assertThat(bob.logged()).hasSize(1);

        final var transcript = new StringBuilder();
        for (final var outcome : List.of(alice, bob)) {
            transcript.append("ApproveExpense(claim = ")
                    .append(outcome == alice ? aliceClaim : bobClaim)
                    .append(", limit = ").append(LIMIT).append(")\n");
            outcome.logged().forEach(line -> transcript.append("    ").append(line).append('\n'));
            transcript.append("    => ").append(outcome.approved() ? "TRUE" : "FALSE").append("\n\n");
        }

        Files.createDirectories(OUTPUTS);
        Files.writeString(OUTPUTS.resolve("five-minutes.txt"),
                transcript.toString().stripTrailing() + "\n", StandardCharsets.UTF_8);
    }

    /**
     * The rejection the tutorial shows, captured from the compiler rather than transcribed.
     * <p>
     * A quoted diagnostic is the first thing to rot when a message is reworded, and rewording
     * messages is routine. This writes whatever the compiler actually said.
     */
    @Test
    void a_total_that_was_never_worked_out_is_refused_at_compile_time() throws IOException {
        final var thrown = catchThrowableOfType(BubasException.class,
                () -> Expense.STAGE_1.compile(source("untotalled-claim.bu")));

        assertThat(thrown).isNotNull();
        assertThat(thrown.getMessage()).contains("'total' is read before it is assigned");

        Files.createDirectories(OUTPUTS);
        Files.writeString(OUTPUTS.resolve("five-minutes-error.txt"),
                thrown.getDiagnostic() + "\n", StandardCharsets.UTF_8);
    }
}
