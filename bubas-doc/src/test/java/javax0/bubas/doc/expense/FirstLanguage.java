package javax0.bubas.doc.expense;

import javax0.bubas.analyser.BubasLanguage;
import javax0.bubas.analyser.BubasProgram;
import javax0.bubas.api.BubasDescription;
import javax0.bubas.api.Context;
import javax0.bubas.api.ExpressionArg;
import javax0.bubas.api.StatementContext;
import javax0.bubas.runtime.Interpreter;
import javax0.bubas.support.Standard;

import java.math.BigDecimal;

/**
 * The smallest complete embedding, written for chapter 21 and for nothing else.
 * <p>
 * The book's own vocabulary is built in stages, so no single method there produces a sealed
 * language — which is right for a book that adds a chapter at a time, and wrong for the first
 * example a reader ever sees. This one is whole: one type, one question, one instruction, sealed,
 * compiled and run.
 */
final class FirstLanguage {

    private FirstLanguage() {
    }

    /** A claim. BUBAS holds one and never looks inside it. */
    @BubasDescription("An expense claim somebody filed.")
    public static final class Claim {
        private final BigDecimal total;

        Claim(String total) {
            this.total = new BigDecimal(total);
        }
    }

    @BubasDescription("What a claim comes to, in euro.")
    public static final class TotalOf {
        public BigDecimal call(Context ctx, Claim expense) {
            return expense.total;
        }
    }

    @BubasDescription("Approves a claim for payment.")
    public static final class Approve {
        public void call(StatementContext ctx, ExpressionArg expense) {
            ctx.log("DECISION", "approved " + expense.evaluate().as(Claim.class).total);
        }
    }

    // snippet: first-language
    /** Built once, at startup, and held for the life of the process. */
    static final BubasLanguage LANGUAGE = BubasLanguage.builder()
            .install(Standard::register)
            .defineOpaqueType("Claim", Claim.class)
            .defineFunction("TOTAL_OF", TotalOf.class)
            .defineStatement("APPROVE {expression/Claim:expense}", Approve.class)
            .seal();
    // end snippet

    // snippet: first-compile
    /** Compiled once per rule text, then reused for every claim that arrives. */
    static final BubasProgram PROGRAM = LANGUAGE.compile(Runs.source("first-rule.bu"));
    // end snippet

    // snippet: first-run
    /** One interpreter per decision. Cheap to make, used once, thrown away. */
    static boolean decide(Claim claim, BigDecimal limit) {
        return Interpreter.of(PROGRAM)
                .argument("expense", claim)
                .argument("limit", limit)
                .run()
                .asBoolean();
    }
    // end snippet
}
