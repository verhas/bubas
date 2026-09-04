package javax0.bubas.doc.expense;

import javax0.bubas.analyser.BubasLanguage;
import javax0.bubas.analyser.BubasProgram;
import javax0.bubas.api.Context;
import javax0.bubas.api.Value;
import javax0.bubas.runtime.Interpreter;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * The wiring chapter's examples, kept here so that they compile.
 * <p>
 * Nothing runs these — they exist to be shown, and to fail the build if the API they show moves.
 */
final class Wiring {

    private Wiring() {
    }

    /** Whatever the application already had. BUBAS never sees this interface. */
    interface ClaimStore {
        BigDecimal totalOf(Expense.Report claim);
    }

    // snippet: thin-handler
    /** A handler translates and delegates. It owns nothing and has no lifecycle. */
    public static final class TotalOf {
        public BigDecimal call(Context ctx, Expense.Report claim) {
            return ctx.service(ClaimStore.class).totalOf(claim);
        }
    }
    // end snippet

    // snippet: rounding
    /**
     * The rounding policy belongs to the language, not to the run. Sixteen significant digits and
     * banker's rounding, decided once, at startup, for every rule this language ever compiles.
     */
    static BubasLanguage roundedToTheCent() {
        return Expense.approving()
                .mathContext(new MathContext(16, RoundingMode.HALF_EVEN))
                .seal();
    }
    // end snippet

    // snippet: per-run-wiring
    /** Services, arguments and the logger are supplied per run, never per language. */
    static Value decide(BubasProgram program, ClaimStore store, Expense.Report claim,
                        BigDecimal limit, java.util.function.BiConsumer<String, String> auditLog) {
        return Interpreter.of(program)
                .registerService(ClaimStore.class, store)
                .argument("claim", claim)
                .argument("limit", limit)
                .logger(auditLog)
                .run();
    }
    // end snippet
}
