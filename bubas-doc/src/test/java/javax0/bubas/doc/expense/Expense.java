package javax0.bubas.doc.expense;

import javax0.bubas.analyser.BubasLanguage;
import javax0.bubas.api.BubasDescribes;
import javax0.bubas.api.BubasDescription;
import javax0.bubas.api.Context;
import javax0.bubas.api.ExpressionArg;
import javax0.bubas.api.StatementContext;
import javax0.bubas.api.VariableArg;
import javax0.bubas.support.Standard;

import java.math.BigDecimal;
import java.util.List;

/**
 * The expense-approval vocabulary the tutorials and the book are written against.
 * <p>
 * The stages are additive and none rewrites an earlier one, so a fix anywhere reaches every
 * document that shows it. That applies to the vocabulary as much as to the programs: each stage is
 * a builder method that calls the one before it, so the five-minute tutorial and the fifteen-minute
 * tutorial are looking at the same definitions rather than at two copies of them.
 * <p>
 * There is deliberately no operation that fetches a claim. The host already holds the claim it
 * decided to run the rule on, and an opaque value can be passed straight in as a program argument.
 * See {@code DOCUMENTATION/AUTHORING.md} D2 and D14.
 */
public final class Expense {

    private Expense() {
    }

    // ---------------------------------------------------------------- the domain

    @BubasDescribes(Report.class)
    @BubasDescription("""
            One employee's expense claim for a trip or a period.
            A program is given one to decide about; ask TOTAL_OF what it comes to.
            """)
    public interface ReportDoc {
    }

    @BubasDescribes(Item.class)
    @BubasDescription("""
            A single line on a claim: what was bought, from whom, for how much.
            Ask AMOUNT_OF, CATEGORY_OF, MERCHANT_OF and HAS_RECEIPT about one.
            """)
    public interface ItemDoc {
    }

    /** One line on a claim. */
    public record Item(String category, String merchant, BigDecimal amount, boolean hasReceipt) {
    }

    /** A value BUBAS holds and passes but cannot look inside. */
    public static final class Report {
        final long id;
        private final String employee;
        private final List<Item> items;

        Report(long id, String employee, List<Item> items) {
            this.id = id;
            this.employee = employee;
            this.items = items;
        }

        BigDecimal total() {
            return items.stream().map(Item::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        @Override
        public String toString() {
            return "report " + id + " (" + employee + ")";
        }
    }

    /** The host constructs claims. The language only ever receives them. */
    static Report claim(long id, String employee, Item... items) {
        return new Report(id, employee, List.of(items));
    }

    static Item item(String category, String merchant, String amount, boolean hasReceipt) {
        return new Item(category, merchant, new BigDecimal(amount), hasReceipt);
    }

    // ---------------------------------------------------------------- stage 1

    // snippet: total-of
    @BubasDescription("What a claim comes to in total, in euro.")
    public static final class TotalOf {
        public BigDecimal call(Context ctx, Report claim) {
            return claim.total();
        }
    }
    // end snippet

    // snippet: approve
    @BubasDescription("Approves a claim for payment.")
    public static final class Approve {
        public void call(StatementContext ctx, ExpressionArg claim) {
            final var filed = claim.evaluate().as(Report.class);
            ctx.log("DECISION", "approved " + filed + " for " + filed.total());
        }
    }
    // end snippet

    @BubasDescription("Refuses a claim, recording the reason the claimant will be shown.")
    public static final class Reject {
        public void call(StatementContext ctx, ExpressionArg claim, ExpressionArg reason) {
            final var filed = claim.evaluate().as(Report.class);
            ctx.log("DECISION", "rejected " + filed + " — " + reason.evaluate().asString());
        }
    }

    // snippet: note
    /**
     * Answers nothing, so it is written as a statement — with or without brackets, because a VOID
     * function may be called either way.
     */
    @BubasDescription("Records a note against the decision, for whoever reads it later.")
    public static final class Note {
        public void call(Context ctx, String message) {
            ctx.log("NOTE", message);
        }
    }
    // end snippet

    // snippet: core-language
    /** Stage 1: what the five-minute tutorial shows. */
    static BubasLanguage.Builder core() {
        return BubasLanguage.builder()
                .install(Standard::register)
                .defineOpaqueTypeVia("Report", ReportDoc.class)
                .defineFunction("TOTAL_OF", TotalOf.class)
                .defineFunction("NOTE", Note.class)
                .defineStatement("APPROVE {expression/Report:claim}", Approve.class)
                .defineStatement("REJECT {expression/Report:claim}, {expression/STRING:reason}",
                        Reject.class);
    }
    // end snippet

    // ---------------------------------------------------------------- stage 2: escalation

    // snippet: escalate
    @BubasDescription("Sends a claim to a manager to decide, with the reason it was not automatic.")
    public static final class Escalate {
        public void call(StatementContext ctx, ExpressionArg claim, ExpressionArg reason) {
            final var filed = claim.evaluate().as(Report.class);
            ctx.log("DECISION", "escalated " + filed + " — " + reason.evaluate().asString());
        }
    }
    // end snippet

    // snippet: escalating-language
    /** Stage 2: one more outcome, so a rule need not choose between yes and no. */
    static BubasLanguage.Builder escalating() {
        return core().defineStatement(
                "ESCALATE {expression/Report:claim}, {expression/STRING:reason}", Escalate.class);
    }
    // end snippet

    // ---------------------------------------------------------------- stage 3: line items

    @BubasDescription("How many lines a claim has.")
    public static final class ItemCount {
        public long call(Context ctx, Report claim) {
            return claim.items.size();
        }
    }

    // snippet: item-at
    @BubasDescription("The line at a position on a claim. The first line is line 1.")
    public static final class ItemAt {
        public Item call(Context ctx, Report claim, long position) {
            return claim.items.get((int) position - 1);
        }
    }
    // end snippet

    @BubasDescription("What a single line came to, in euro.")
    public static final class AmountOf {
        public BigDecimal call(Context ctx, Item line) {
            return line.amount();
        }
    }

    @BubasDescription("What kind of spending a line is: travel, meals, lodging.")
    public static final class CategoryOf {
        public String call(Context ctx, Item line) {
            return line.category();
        }
    }

    @BubasDescription("Who the money was paid to on a line.")
    public static final class MerchantOf {
        public String call(Context ctx, Item line) {
            return line.merchant();
        }
    }

    @BubasDescription("Whether the claimant attached a receipt to a line.")
    public static final class HasReceipt {
        public boolean call(Context ctx, Item line) {
            return line.hasReceipt();
        }
    }

    // snippet: itemised-language
    /** Stage 3: the claim stops being a single number and becomes a list of lines. */
    static BubasLanguage.Builder itemised() {
        return escalating()
                .defineOpaqueTypeVia("Item", ItemDoc.class)
                .defineFunction("ITEM_COUNT", ItemCount.class)
                .defineFunction("ITEM_AT", ItemAt.class)
                .defineFunction("AMOUNT_OF", AmountOf.class)
                .defineFunction("CATEGORY_OF", CategoryOf.class)
                .defineFunction("MERCHANT_OF", MerchantOf.class)
                .defineFunction("HAS_RECEIPT", HasReceipt.class);
    }
    // end snippet

    // ---------------------------------------------------------------- stage 4: asking directly

    // snippet: total-for
    @BubasDescription("What one category of spending on a claim comes to, in euro.")
    public static final class TotalFor {
        public BigDecimal call(Context ctx, Report claim, String category) {
            return claim.items.stream()
                    .filter(line -> line.category().equals(category))
                    .map(Item::amount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }
    // end snippet

    // snippet: categorised-language
    /**
     * Stage 4: one operation that replaces a loop, an array and a category-to-slot mapping in
     * every rule that would otherwise have had to write them.
     */
    static BubasLanguage.Builder categorised() {
        return itemised().defineFunction("TOTAL_FOR", TotalFor.class);
    }
    // end snippet

    // ---------------------------------------------------------------- stage 5: an opinion

    // snippet: anomaly-score
    /**
     * Stands in for a model. A real implementation would send the line to a service and return
     * what it answered; this one is a fixed rule of thumb, because the build must run offline for
     * anyone, forever, and a book whose examples change between printings is no use.
     * <p>
     * It returns a <em>score</em>, never a verdict. What counts as too high is a threshold in the
     * rule, where the person accountable for the policy can read and change it. See
     * {@code DOCUMENTATION/AUTHORING.md} D10.
     */
    @BubasDescription("""
            How unusual a line of spending looks, from 1 (ordinary) to 10 (very unusual).
            It is an opinion, not a decision: the rule decides what score is too high.
            """)
    public static final class AnomalyScoreOf {
        private static final BigDecimal LARGE = new BigDecimal("100");
        private static final BigDecimal VERY_LARGE = new BigDecimal("500");
        private static final BigDecimal LAVISH_MEAL = new BigDecimal("75");

        public long call(Context ctx, Item line) {
            var score = 1L;
            if (line.amount().compareTo(LARGE) > 0) {
                score += 2;
            }
            if (line.amount().compareTo(VERY_LARGE) > 0) {
                score += 3;
            }
            if (!line.hasReceipt()) {
                score += 2;
            }
            if ("meals".equals(line.category()) && line.amount().compareTo(LAVISH_MEAL) > 0) {
                score += 3;
            }
            return Math.min(score, 10);
        }
    }
    // end snippet

    // snippet: screening-language
    /** Stage 5: an operation that has an opinion rather than an answer. */
    static BubasLanguage.Builder screening() {
        return categorised().defineFunction("ANOMALY_SCORE_OF", AnomalyScoreOf.class);
    }
    // end snippet

    // ---------------------------------------------------------------- stage 6: routing

    @BubasDescribes(CostCentre.class)
    @BubasDescription("""
            The budget a claim will be charged against, and the thing an approver signs for.
            Ask BUDGET_LEFT how much of it is still unspent this period.
            """)
    public interface CostCentreDoc {
    }

    /** Where the money comes from. Opaque, like everything else the domain owns. */
    public record CostCentre(String code, BigDecimal remaining) {
        @Override
        public String toString() {
            return "cost centre " + code;
        }
    }

    // snippet: route
    /**
     * Two answers from one lookup, which is the only reason this is a command rather than a
     * function. Who signs and which budget it lands on are decided together, by one reading of the
     * approval policy; asking twice could get answers from two different readings.
     */
    @BubasDescription("""
            Works out who has to approve a claim and which budget it will be charged to.
            Both come from one reading of the approval policy, so they are always consistent.
            """)
    public static final class Route {
        public void call(StatementContext ctx, ExpressionArg claim, VariableArg approver,
                         VariableArg centre) {
            final var filed = claim.evaluate().as(Report.class);
            approver.set(filed.total().compareTo(new BigDecimal("1000")) > 0
                    ? "the finance director" : "the line manager");
            centre.set(new CostCentre("CC-" + filed.id, new BigDecimal("2500.00")));
        }
    }
    // end snippet

    @BubasDescription("How much of a cost centre's budget is still unspent, in euro.")
    public static final class BudgetLeft {
        public BigDecimal call(Context ctx, CostCentre centre) {
            return centre.remaining();
        }
    }

    // snippet: routing-language
    /** Stage 6: one operation, two answers, and the budget they point at. */
    static BubasLanguage.Builder routing() {
        return screening()
                .defineOpaqueTypeVia("CostCentre", CostCentreDoc.class)
                .defineFunction("BUDGET_LEFT", BudgetLeft.class)
                .defineStatement("ROUTE {expression/Report:claim}"
                        + " TO {new > identifier/STRING:approver > initialized}"
                        + " AT {new > identifier/CostCentre:centre > initialized}", Route.class);
    }
    // end snippet

    // ---------------------------------------------------------------- the sealed stages

    public static final BubasLanguage STAGE_1 = core().seal();
    public static final BubasLanguage STAGE_2 = escalating().seal();
    public static final BubasLanguage STAGE_3 = itemised().seal();
    public static final BubasLanguage STAGE_4 = categorised().seal();
    public static final BubasLanguage STAGE_5 = screening().seal();
    public static final BubasLanguage STAGE_6 = routing().seal();
}
