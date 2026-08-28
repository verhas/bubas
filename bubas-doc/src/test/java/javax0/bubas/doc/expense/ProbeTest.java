package javax0.bubas.doc.expense;

import javax0.bubas.analyser.BubasLanguage;
import javax0.bubas.api.Context;
import javax0.bubas.bunit.standard.BunitSuite;
import javax0.bubas.support.Standard;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

class ProbeTest {

    public static final class Load {
        public Expense.Report call(Context ctx, long id) {
            throw new IllegalStateException("must be mocked");
        }
    }

    private static BubasLanguage.Builder base() {
        return BubasLanguage.builder()
                .install(Standard::register)
                .defineOpaqueTypeVia("Report", Expense.ReportDoc.class)
                .defineFunction("TOTAL_OF", Expense.TotalOf.class)
                .defineStatement("APPROVE {expression/Report:claim}", Expense.Approve.class)
                .defineStatement("REJECT {expression/Report:claim}, {expression/STRING:reason}",
                        Expense.Reject.class);
    }

    private static final BubasLanguage PASSED_IN = base().seal();
    private static final BubasLanguage FETCHED = base().defineFunction("LOAD", Load.class).seal();

    private static String read(String path) {
        try (var s = ProbeTest.class.getResourceAsStream(path)) {
            return new String(java.util.Objects.requireNonNull(s, path).readAllBytes(),
                    StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void show(String label, BubasLanguage language, String subject, String test) {
        final var r = BunitSuite.of(language, read(subject)).run(read(test));
        System.out.println("### " + label);
        System.out.println("passed = " + r.passed());
        System.out.println(r.diagnostic() == null ? "(no diagnostic)" : r.diagnostic());
        System.out.println("calls  = " + r.calls());
    }

    @Test
    void probe() {
        show("A: claim passed in as a parameter", PASSED_IN,
                "/probe/passed-in.bu", "/probe/passed-in-test.bu");
        show("B: fetched, expectation WITHOUT ARGS", FETCHED,
                "/probe/fetched.bu", "/probe/fetched-test.bu");
        show("C: fetched, expectation WITH ARGS naming the token", FETCHED,
                "/probe/fetched.bu", "/probe/fetched-test2.bu");
    }
}
