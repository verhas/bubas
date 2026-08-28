package javax0.bubas.doc.expense;

import javax0.bubas.analyser.BubasProgram;
import javax0.bubas.runtime.Interpreter;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Runs a program from the corpus and writes what it actually printed to {@code target/doc-outputs/}.
 * <p>
 * The documents include those files. Nothing in any tutorial or chapter quotes an output that a
 * human typed, so a changed rule, a reworded message or a different total shows up as a
 * documentation diff on the build that caused it. See {@code DOCUMENTATION/AUTHORING.md} D6.
 */
final class Runs {

    private static final Path OUTPUTS = Path.of("target", "doc-outputs");

    private Runs() {
    }

    /** One run: what it was called with, what it said, what it answered. */
    record Outcome(String program, Map<String, Object> arguments, boolean answer,
                   List<String> logged) {
    }

    static String source(String name) {
        try (var stream = Runs.class.getResourceAsStream("/programs/" + name)) {
            return new String(Objects.requireNonNull(stream, name).readAllBytes(),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + name, e);
        }
    }

    /** Arguments in the order the program declares them, which is the order transcripts show. */
    static Map<String, Object> args(Object... pairs) {
        final var map = new LinkedHashMap<String, Object>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }

    static Outcome run(BubasProgram program, Map<String, Object> arguments) {
        final var logged = new ArrayList<String>();
        final var interpreter = Interpreter.of(program);
        arguments.forEach(interpreter::argument);
        final var answer = interpreter
                .logger((level, message) -> logged.add(message))
                .run()
                .asBoolean();
        return new Outcome(program.name(), arguments, answer, List.copyOf(logged));
    }

    /** The common case: a claim and a limit. */
    static Outcome run(BubasProgram program, Expense.Report claim, BigDecimal limit) {
        return run(program, args("claim", claim, "limit", limit));
    }

    static void write(String name, String content) throws IOException {
        Files.createDirectories(OUTPUTS);
        Files.writeString(OUTPUTS.resolve(name), content.stripTrailing() + "\n",
                StandardCharsets.UTF_8);
    }

    /** Booleans are shown as the language writes them, not as Java does. */
    private static String render(Object value) {
        return value instanceof Boolean flag ? (flag ? "TRUE" : "FALSE") : String.valueOf(value);
    }

    /** The transcript shape every document shows: the call, what it said, what it answered. */
    static String transcript(List<Outcome> outcomes) {
        final var out = new StringBuilder();
        for (final var outcome : outcomes) {
            out.append(outcome.program()).append('(')
                    .append(outcome.arguments().entrySet().stream()
                            .map(e -> e.getKey() + " = " + render(e.getValue()))
                            .collect(Collectors.joining(", ")))
                    .append(")\n");
            outcome.logged().forEach(line -> out.append("    ").append(line).append('\n'));
            out.append("    => ").append(outcome.answer() ? "TRUE" : "FALSE").append("\n\n");
        }
        return out.toString();
    }
}
