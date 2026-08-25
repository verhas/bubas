package javax0.bubas.bunit;

import java.util.List;

/**
 * What a test run produced.
 * <p>
 * This record is the product of BUNIT, not the console output: a CLI, a REST endpoint and an MCP
 * tool are each a short adapter over it, and none of them should have to parse text to find out
 * what happened.
 *
 * @param name       the test program's own name
 * @param passed     whether every expectation held
 * @param diagnostic why it failed, with the line and the source line, or {@code null} when it passed
 * @param calls      every call the subject made, in order, whether mocked or not
 * @param log        whatever the subject logged, kept for reading a failure rather than asserting on
 */
public record TestResult(String name, boolean passed, String diagnostic, List<String> calls,
                         List<String> log) {

    public static TestResult passed(String name, List<String> calls, List<String> log) {
        return new TestResult(name, true, null, List.copyOf(calls), List.copyOf(log));
    }

    public static TestResult failed(String name, String diagnostic, List<String> calls,
                                    List<String> log) {
        return new TestResult(name, false, diagnostic, List.copyOf(calls), List.copyOf(log));
    }

    @Override
    public String toString() {
        return (passed ? "PASSED " : "FAILED ") + name + (passed ? "" : "\n" + diagnostic);
    }
}
