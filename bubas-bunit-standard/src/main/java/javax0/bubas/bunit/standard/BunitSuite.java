package javax0.bubas.bunit.standard;

import javax0.bubas.analyser.BubasLanguage;
import javax0.bubas.api.BubasException;
import javax0.bubas.bunit.BunitRunner;
import javax0.bubas.bunit.TestResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Runs BUBAS unit tests against one subject.
 * <p>
 * The subject is compiled once and every test reuses it, because a {@code BubasProgram} is
 * immutable and only the interpreter is per-run. That is also why a suite of a hundred tests costs
 * one compilation of the subject rather than a hundred.
 * <p>
 * A failing test is a result, not an exception. The only thing that throws is a subject that does
 * not compile, which is a fault in the subject rather than an outcome of testing it.
 */
public final class BunitSuite {

    private final BunitRunner runner;

    private BunitSuite(BubasLanguage subjectLanguage, String subject) {
        this.runner = BunitRunner.of(BunitLanguage.get(), subjectLanguage, subject);
    }

    /**
     * @param subjectLanguage the language the subject is written against — the embedder's own
     * @param subject         the BUBAS program under test
     * @throws BubasException when the subject does not compile
     */
    public static BunitSuite of(BubasLanguage subjectLanguage, String subject) {
        return new BunitSuite(subjectLanguage, subject);
    }

    /** Runs one test. */
    public TestResult run(String test) {
        return runner.run(test);
    }

    /**
     * Runs every test, in order, and returns every result — including the ones that failed.
     * Stopping at the first failure would hide how much else is broken, which is the one thing a
     * suite is for.
     */
    public List<TestResult> runAll(List<String> tests) {
        final var results = new ArrayList<TestResult>();
        tests.forEach(test -> results.add(run(test)));
        return List.copyOf(results);
    }

    /** Runs named tests, so a report can say which file failed rather than which index. */
    public List<TestResult> runAll(Map<String, String> tests) {
        return runAll(List.copyOf(tests.values()));
    }

    /** Whether every result passed. */
    public static boolean allPassed(List<TestResult> results) {
        return results.stream().allMatch(TestResult::passed);
    }

    /** One line per test, failures carrying their diagnostic — enough for a CLI to print. */
    public static String report(List<TestResult> results) {
        final var passed = results.stream().filter(TestResult::passed).count();
        final var out = new StringBuilder()
                .append(passed).append('/').append(results.size()).append(" passed\n");
        results.forEach(result -> out.append(result).append('\n'));
        return out.toString();
    }
}
