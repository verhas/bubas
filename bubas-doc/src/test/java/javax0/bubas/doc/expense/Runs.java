package javax0.bubas.doc.expense;

import javax0.bubas.analyser.BubasProgram;
import javax0.bubas.runtime.Interpreter;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Runs a tutorial program and writes what it actually printed to {@code target/doc-outputs/}.
 * <p>
 * The documents include those files. Nothing in the prose quotes an output that a human typed, so a
 * changed rule, a reworded message or a different total shows up as a documentation diff on the
 * build that caused it. See {@code DOCUMENTATION/AUTHORING.md} D6.
 */
final class Runs {

    private static final Path OUTPUTS = Path.of("target", "doc-outputs");

    private Runs() {
    }

    /** One run of one program against one claim. */
    record Outcome(Expense.Report claim, boolean approved, List<String> logged) {
    }

    static String source(String name) {
        try (var stream = Runs.class.getResourceAsStream("/programs/" + name)) {
            return new String(Objects.requireNonNull(stream, name).readAllBytes(),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + name, e);
        }
    }

    static Outcome run(BubasProgram program, Expense.Report claim, BigDecimal limit) {
        final var logged = new ArrayList<String>();
        final var approved = Interpreter.of(program)
                .argument("claim", claim)
                .argument("limit", limit)
                .logger((level, message) -> logged.add(message))
                .run()
                .asBoolean();
        return new Outcome(claim, approved, List.copyOf(logged));
    }

    static void write(String name, String content) throws IOException {
        Files.createDirectories(OUTPUTS);
        Files.writeString(OUTPUTS.resolve(name), content.stripTrailing() + "\n",
                StandardCharsets.UTF_8);
    }

    /** The transcript shape every tutorial shows: the call, what it said, what it answered. */
    static String transcript(BigDecimal limit, List<Outcome> outcomes) {
        final var out = new StringBuilder();
        for (final var outcome : outcomes) {
            out.append("ApproveExpense(claim = ").append(outcome.claim())
                    .append(", limit = ").append(limit).append(")\n");
            outcome.logged().forEach(line -> out.append("    ").append(line).append('\n'));
            out.append("    => ").append(outcome.approved() ? "TRUE" : "FALSE").append("\n\n");
        }
        return out.toString();
    }
}
