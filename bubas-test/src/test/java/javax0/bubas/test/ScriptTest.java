package javax0.bubas.test;

import javax0.bubas.analyser.BubasLanguage;
import javax0.bubas.api.BubasException;
import javax0.bubas.runtime.Interpreter;
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
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.fail;

/**
 * Runs every script in the corpus through the whole stack — lexer, parser, checks, lowering,
 * execution — and holds it to what its own header says should happen.
 * <p>
 * Each script is one test, named after its file, so a failure names the script rather than a line
 * in this class. Scripts are the readable form of the specification's rules: a rule that cannot be
 * demonstrated by a short script is usually a rule worth re-reading.
 */
class ScriptTest {

    private static final BubasLanguage LANGUAGE = Environment.language();

    @TestFactory
    Stream<DynamicTest> everyScript() throws IOException, URISyntaxException {
        final var directory = Path.of(
                ScriptTest.class.getResource("/scripts").toURI());
        try (var files = Files.walk(directory)) {
            return files.filter(path -> path.toString().endsWith(".bu"))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList().stream()
                    .map(path -> DynamicTest.dynamicTest(
                            directory.relativize(path).toString(), () -> run(path)));
        }
    }

    private void run(Path path) throws IOException {
        final var source = Files.readString(path, StandardCharsets.UTF_8);
        final var expectation = Expectation.of(List.of(source.split("\\R", -1)));
        switch (expectation.outcome()) {
            case NO_COMPILE -> check(expectation, "compile",
                    catchThrowableOfType(BubasException.class, () -> LANGUAGE.compile(source)));
            case RUN_TIME_ERROR -> {
                final var program = compiled(source, expectation);
                check(expectation, "run", catchThrowableOfType(BubasException.class,
                        () -> Interpreter.of(program).logger((level, message) -> {
                        }).run()));
            }
            case OK -> {
                final var program = compiled(source, expectation);
                final var thrown = catchThrowableOfType(BubasException.class,
                        () -> Interpreter.of(program).logger((level, message) -> {
                        }).run());
                if (thrown != null) {
                    fail(describe(expectation) + " was expected to run cleanly but failed:\n"
                            + thrown.getDiagnostic());
                }
            }
        }
    }

    private static javax0.bubas.analyser.BubasProgram compiled(String source,
                                                               Expectation expectation) {
        final var thrown = catchThrowableOfType(BubasException.class,
                () -> LANGUAGE.compile(source));
        if (thrown != null) {
            fail(describe(expectation) + " was expected to compile but did not:\n"
                    + thrown.getDiagnostic());
        }
        return LANGUAGE.compile(source);
    }

    private static void check(Expectation expectation, String stage, BubasException thrown) {
        if (thrown == null) {
            fail(describe(expectation) + " was expected to fail at " + stage + ", but did not");
        }
        if (expectation.message() != null) {
            assertThat(thrown.getMessage())
                    .as("%s: the %s diagnostic", describe(expectation), stage)
                    .contains(expectation.message());
        }
        if (expectation.line() != null) {
            assertThat(thrown.getLine())
                    .as("%s: the line the %s diagnostic names%n%s", describe(expectation), stage,
                            thrown.getDiagnostic())
                    .isEqualTo(expectation.line());
        }
    }

    private static String describe(Expectation expectation) {
        return expectation.what().isEmpty() ? "this script" : "\"" + expectation.what() + "\"";
    }
}
