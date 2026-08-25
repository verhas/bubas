package javax0.bubas.bunit;

import javax0.bubas.analyser.BubasLanguage;
import javax0.bubas.api.BubasException;
import javax0.bubas.runtime.Interpreter;

/**
 * Runs one BUBAS unit test.
 * <p>
 * Two programs and two languages. The test compiles against whichever language the caller
 * assembled its statements into; the subject compiles against the embedder's own, unchanged — which
 * is the point of mocking by interception rather than by building a parallel vocabulary. What runs
 * under test is what ships.
 * <p>
 * A subject is compiled once and may be tested many times: {@code BubasProgram} is reusable, and
 * only the {@link Interpreter} and the {@link Recorder} are per-run.
 */
public final class BunitRunner {

    private final BubasLanguage testLanguage;
    private final BubasLanguage language;
    private final javax0.bubas.analyser.BubasProgram subject;

    private BunitRunner(BubasLanguage testLanguage, BubasLanguage language, String subjectSource) {
        this.testLanguage = testLanguage;
        this.language = language;
        this.subject = language.compile(subjectSource);
    }

    /**
     * @param testLanguage the language the <em>test</em> is written in — the statements plus
     *                     whatever else the caller installed. The framework takes it rather than
     *                     owning it, so it holds no opinion about which vocabulary a test uses
     * @param language     the language the subject is written against
     * @param subject      the subject's source
     * @throws BubasException when the subject does not compile — a fault in the subject, reported
     *                        as itself rather than dressed up as a test failure
     */
    public static BunitRunner of(BubasLanguage testLanguage, BubasLanguage language,
                                 String subject) {
        return new BunitRunner(testLanguage, language, subject);
    }

    /**
     * Compiles and runs one test against the subject.
     * <p>
     * A failed expectation is an ordinary {@link BubasException} raised by the statement that
     * failed, so it arrives carrying the line of that expectation. It becomes a failed result
     * rather than propagating: a test that fails is an outcome, not an error.
     */
    public TestResult run(String testSource) {
        final var program = testLanguage.compile(testSource);
        final var recorder = new Recorder(language, subject);
        try {
            Interpreter.of(program)
                    .registerService(MockRecorder.class, recorder)
                    .logger((level, message) -> {
                    })
                    .run();
        } catch (BubasException e) {
            return TestResult.failed(program.name(), e.getDiagnostic(),
                    recorder.transcript(), recorder.log());
        }
        return TestResult.passed(program.name(), recorder.transcript(), recorder.log());
    }
}
