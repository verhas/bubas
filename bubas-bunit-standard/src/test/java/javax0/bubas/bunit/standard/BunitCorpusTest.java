package javax0.bubas.bunit.standard;

import javax0.bubas.bunit.TestResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Every BUNIT test in {@code /bunit/tests}, run against the subject it names.
 * <p>
 * A corpus rather than more Java text blocks, for the reason the language has one: these are the
 * artefact a person actually writes, and reading a directory of them tells you what the framework
 * can do far better than reading assertions about strings. The language's corpus repeatedly caught
 * what its unit tests had not.
 * <p>
 * Two files per case, because a test of a test needs both halves. The test names its subject in its
 * own header:
 *
 * <pre>
 * 'PASS                        or 'FAIL, or 'REJECT
 * ' What this checks, in a sentence.
 * ' SUBJECT: approve-order.bu
 * ' ERROR: a fragment the diagnostic must contain
 * </pre>
 *
 * {@code PASS} expects every expectation to hold. {@code FAIL} expects the test to run and an
 * expectation to fail. {@code REJECT} expects the consistency checker to refuse it before the
 * subject runs at all, which is asserted rather than assumed: nothing may have been called.
 */
@DisplayName("the BUNIT corpus")
class BunitCorpusTest {

    private enum Outcome {
        PASS, FAIL, REJECT
    }

    private record Expectation(Outcome outcome, String what, String subject, String error) {

        static Expectation of(List<String> lines) {
            Outcome outcome = null;
            final var what = new StringBuilder();
            String subject = null;
            String error = null;
            for (final var line : lines) {
                final var text = line.strip();
                if (!text.startsWith("'")) {
                    break;
                }
                final var body = text.substring(1).strip();
                if (outcome == null) {
                    outcome = Outcome.valueOf(body.replace('-', '_'));
                } else if (body.startsWith("SUBJECT:")) {
                    subject = body.substring("SUBJECT:".length()).strip();
                } else if (body.startsWith("ERROR:")) {
                    error = body.substring("ERROR:".length()).strip();
                } else {
                    what.append(what.isEmpty() ? "" : " ").append(body);
                }
            }
            return new Expectation(outcome, what.toString(), subject, error);
        }
    }

    @TestFactory
    Stream<DynamicTest> everyTest() throws IOException, URISyntaxException {
        final var root = Path.of(BunitCorpusTest.class.getResource("/bunit").toURI());
        try (var files = Files.list(root.resolve("tests"))) {
            return files.filter(path -> path.toString().endsWith(".bu"))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList().stream()
                    .map(path -> DynamicTest.dynamicTest(path.getFileName().toString(),
                            () -> run(root, path)));
        }
    }

    private void run(Path root, Path path) throws IOException {
        final var source = Files.readString(path, StandardCharsets.UTF_8);
        final var expectation = Expectation.of(List.of(source.split("\\R", -1)));
        if (expectation.outcome() == null) {
            fail(path.getFileName() + " has no outcome on its first line");
        }
        if (expectation.subject() == null) {
            fail(path.getFileName() + " names no SUBJECT");
        }
        final var subject = Files.readString(
                root.resolve("subjects").resolve(expectation.subject()), StandardCharsets.UTF_8);
        final var result = BunitSuite.of(Corpus.LANGUAGE, subject).run(source);
        switch (expectation.outcome()) {
            case PASS -> {
                if (!result.passed()) {
                    fail(describe(expectation) + " was expected to pass:\n" + result.diagnostic());
                }
            }
            case FAIL -> {
                assertThat(result.passed()).as("%s was expected to fail", describe(expectation))
                        .isFalse();
                contains(expectation, result);
            }
            case REJECT -> {
                assertThat(result.passed()).as("%s was expected to be refused", describe(expectation))
                        .isFalse();
                assertThat(result.calls())
                        .as("%s: refused before the subject runs, so nothing may have been called",
                                describe(expectation))
                        .isEmpty();
                contains(expectation, result);
            }
        }
    }

    private static void contains(Expectation expectation, TestResult result) {
        if (expectation.error() != null) {
            assertThat(result.diagnostic())
                    .as("%s: the diagnostic", describe(expectation))
                    .contains(expectation.error());
        }
    }

    private static String describe(Expectation expectation) {
        return expectation.what().isEmpty() ? "this test" : '"' + expectation.what() + '"';
    }
}
