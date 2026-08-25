package javax0.bubas.bunit;

import javax0.bubas.analyser.BubasLanguage;
import javax0.bubas.api.Context;
import javax0.bubas.api.ExpressionArg;
import javax0.bubas.api.StatementContext;
import javax0.bubas.support.Standard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("running a test against a subject")
class BunitRunnerTest {

    public static final class Order {
    }

    public static final class LoadOrder {
        public Order call(Context ctx, long orderId) {
            throw new IllegalStateException("the real implementation must never run under test");
        }
    }

    public static final class OrderTotal {
        public BigDecimal call(Context ctx, Order order) {
            throw new IllegalStateException("the real implementation must never run under test");
        }
    }

    public static final class Approve {
        public void call(StatementContext ctx, ExpressionArg target) {
            throw new IllegalStateException("the real implementation must never run under test");
        }
    }

    public static final class LogEvent {
        public void call(StatementContext ctx, ExpressionArg level, ExpressionArg message) {
            ctx.log(level.evaluate().asString(), message.evaluate().asString());
        }
    }

    private static final BubasLanguage LANGUAGE = BubasLanguage.builder()
            .install(Standard::register)
            .defineOpaqueType("Order", Order.class)
            .defineFunction("LOAD_ORDER", LoadOrder.class)
            .defineFunction("ORDER_TOTAL", OrderTotal.class)
            .defineStatement("APPROVE {expression/Order:target}", Approve.class)
            .defineStatement("LOG_EVENT {expression:level}, {expression:message}", LogEvent.class)
            .seal();

    private static final String SUBJECT = """
            PROGRAM ApproveOrder(orderId INTEGER, limit DECIMAL) RETURNS BOOLEAN
                DECLARE purchase Order
                DECLARE total DECIMAL

                purchase = LOAD_ORDER(orderId)
                total = ORDER_TOTAL(purchase)

                IF total > limit THEN
                    LOG_EVENT "INFO", "over limit"
                    RETURN FALSE
                END IF

                APPROVE purchase
                RETURN TRUE
            END.
            """;

    private static TestResult run(String test) {
        return BunitRunner.of(LANGUAGE, SUBJECT).run(test);
    }

    @Test
    void a_passing_test_reports_what_the_subject_did() {
        final var result = run("""
                PROGRAM OverLimitIsRejected
                    "LOAD_ORDER"  WITH 42   RETURNS "o1"
                    "ORDER_TOTAL" WITH "o1" RETURNS 1500.00
                    "APPROVE _" IS MOCKED

                    ARGUMENT "orderId" IS 42
                    ARGUMENT "limit"   IS 1000.00

                    RUN

                    RESULT IS FALSE
                    "APPROVE _" WAS NOT CALLED
                END.
                """);
        assertThat(result.passed()).as("%s", result.diagnostic()).isTrue();
        assertThat(result.name()).isEqualTo("OverLimitIsRejected");
        assertThat(result.calls()).containsExactly("LOAD_ORDER(42)", "ORDER_TOTAL(o1)");
        assertThat(result.log()).containsExactly("INFO: over limit");
    }

    @Test
    void a_token_flows_from_one_mock_into_the_next() {
        final var result = run("""
                PROGRAM UnderLimitIsApproved
                    "LOAD_ORDER"  WITH 7    RETURNS "o1"
                    "ORDER_TOTAL" WITH "o1" RETURNS 10.00
                    "APPROVE _" IS MOCKED

                    ARGUMENT "orderId" IS 7
                    ARGUMENT "limit"   IS 1000.00

                    RUN

                    RESULT IS TRUE
                    "APPROVE _" WAS CALLED
                END.
                """);
        assertThat(result.passed()).as("%s", result.diagnostic()).isTrue();
    }

    @Test
    void a_failed_expectation_names_its_own_line() {
        final var result = run("""
                PROGRAM WrongExpectation
                    "LOAD_ORDER"  WITH 7    RETURNS "o1"
                    "ORDER_TOTAL" WITH "o1" RETURNS 10.00
                    "APPROVE _" IS MOCKED
                    ARGUMENT "orderId" IS 7
                    ARGUMENT "limit"   IS 1000.00
                    RUN
                    RESULT IS FALSE
                END.
                """);
        assertThat(result.passed()).isFalse();
        assertThat(result.diagnostic())
                .contains("expected the result to be false, but it was true")
                .contains("line 8");
    }

    @Test
    void a_mock_answering_whatever_it_is_given() {
        final var result = run("""
                PROGRAM AnyArguments
                    "LOAD_ORDER" RETURNS "o1"
                    "ORDER_TOTAL" RETURNS 10.00
                    "APPROVE _" IS MOCKED
                    ARGUMENT "orderId" IS 99
                    ARGUMENT "limit" IS 1000.00
                    RUN
                    RESULT IS TRUE
                END.
                """);
        assertThat(result.passed()).as("%s", result.diagnostic()).isTrue();
    }

    /** The path the first version of this suite never exercised: a command's own arguments. */
    @Test
    void a_mocked_command_records_the_arguments_it_was_given() {
        final var result = run("""
                PROGRAM CommandArguments
                    "LOAD_ORDER" RETURNS "o1"
                    "ORDER_TOTAL" RETURNS 1500.00
                    "LOG_EVENT _, _" IS MOCKED
                    ARGUMENT "orderId" IS 1
                    ARGUMENT "limit" IS 10.00
                    RUN
                    RESULT IS FALSE
                    "LOG_EVENT _, _" WAS CALLED WITH "INFO", "over limit"
                END.
                """);
        assertThat(result.passed()).as("%s", result.diagnostic()).isTrue();
        assertThat(result.calls()).contains("LOG_EVENT _, _(\"INFO\", \"over limit\")");
        assertThat(result.log()).as("a mocked command does not reach its handler").isEmpty();
    }

    @Test
    void a_wrong_command_argument_is_reported() {
        final var result = run("""
                PROGRAM WrongCommandArgument
                    "LOAD_ORDER" RETURNS "o1"
                    "ORDER_TOTAL" RETURNS 1500.00
                    "LOG_EVENT _, _" IS MOCKED
                    ARGUMENT "orderId" IS 1
                    ARGUMENT "limit" IS 10.00
                    RUN
                    "LOG_EVENT _, _" WAS CALLED WITH "WARN", "over limit"
                END.
                """);
        assertThat(result.passed()).isFalse();
        assertThat(result.diagnostic())
                .contains("expected LOG_EVENT _, _ to be called with (\"WARN\", \"over limit\")")
                .contains("but it was called with (\"INFO\", \"over limit\")");
    }

    @Test
    void an_expectation_before_RUN_is_refused() {
        final var result = run("""
                PROGRAM NoAct
                    "LOAD_ORDER" RETURNS "o1"
                    RESULT IS TRUE
                END.
                """);
        assertThat(result.passed()).isFalse();
        assertThat(result.diagnostic()).contains("the subject has not been run yet");
    }
}
