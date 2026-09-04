package javax0.bubas.bunit.standard;

import javax0.bubas.analyser.BubasLanguage;
import javax0.bubas.api.Context;
import javax0.bubas.api.ExpressionArg;
import javax0.bubas.api.StatementContext;
import javax0.bubas.api.VariableArg;
import javax0.bubas.bunit.BunitRunner;
import javax0.bubas.bunit.TestResult;
import javax0.bubas.support.Standard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("checking a test before it runs")
class MockConsistencyTest {

    public static final class Order {
    }

    public static final class LoadOrder {
        public Order call(Context ctx, long id) {
            return new Order();
        }
    }

    public static final class OrderTotal {
        public BigDecimal call(Context ctx, Order order) {
            return BigDecimal.ONE;
        }
    }

    /** Writes an INTEGER, so a mock must supply it. */
    public static final class CountInto {
        public void call(StatementContext ctx, VariableArg total, ExpressionArg region) {
        }
    }

    /** Writes an opaque value, so the framework supplies a token. */
    public static final class FetchInto {
        public void call(StatementContext ctx, VariableArg target, ExpressionArg id) {
        }
    }

    public static final class Approve {
        public void call(StatementContext ctx, ExpressionArg target) {
        }
    }

    private static final BubasLanguage SUBJECT = BubasLanguage.builder()
            .install(Standard::register)
            .defineOpaqueType("Order", Order.class)
            .defineFunction("LOAD_ORDER", LoadOrder.class)
            .defineFunction("ORDER_TOTAL", OrderTotal.class)
            .defineStatement("COUNT ORDERS INTO {new > identifier/INTEGER:total > initialized}"
                    + " FOR {expression:region}", CountInto.class)
            .defineStatement("FETCH {new > identifier/Order:target > initialized}"
                    + " BY {expression:id}", FetchInto.class)
            .defineStatement("APPROVE {expression/Order:target}", Approve.class)
            .seal();

    private static final String SUBJECT_SOURCE = """
            PROGRAM Counting RETURNS INTEGER
                COUNT ORDERS INTO total FOR "EU"
                RETURN total
            END.
            """;

    private static TestResult check(String test) {
        return BunitRunner.of(BunitLanguage.get(), SUBJECT, SUBJECT_SOURCE).run(test);
    }

    @Test
    void a_supply_in_only_one_branch_is_not_a_supply() {
        final var result = check("""
                PROGRAM SuppliedOnOnePathOnly
                    DECLARE once[1] INTEGER
                    "COUNT ORDERS INTO _ FOR _" IS MOCKED
                    IF LENGTH(once) = 1 THEN
                        "COUNT ORDERS INTO _ FOR _" SETS "total" TO 42
                    END IF
                    RUN
                    RESULT IS 42
                END.
                """);
        assertThat(result.passed()).isFalse();
        assertThat(result.diagnostic()).contains("nothing supplies it on every path");
    }

    @Test
    void a_supply_in_every_branch_is_a_supply() {
        final var result = check("""
                PROGRAM SuppliedEverywhere
                    DECLARE once[1] INTEGER
                    "COUNT ORDERS INTO _ FOR _" IS MOCKED
                    IF LENGTH(once) = 1 THEN
                        "COUNT ORDERS INTO _ FOR _" SETS "total" TO 42
                    ELSE
                        "COUNT ORDERS INTO _ FOR _" SETS "total" TO 7
                    END IF
                    RUN
                    RESULT IS 42
                END.
                """);
        assertThat(result.passed()).as("%s", result.diagnostic()).isTrue();
    }

    @Test
    void an_opaque_write_needs_no_supply() {
        final var subject = """
                PROGRAM Fetching RETURNS DECIMAL
                    FETCH purchase BY 1
                    RETURN ORDER_TOTAL(purchase)
                END.
                """;
        final var result = BunitRunner.of(BunitLanguage.get(), SUBJECT, subject).run("""
                PROGRAM OpaqueIsAutomatic
                    "FETCH _ BY _" IS MOCKED
                    "ORDER_TOTAL" RETURNS 9.00
                    RUN
                    RESULT IS 9.00
                END.
                """);
        assertThat(result.passed()).as("%s", result.diagnostic()).isTrue();
    }

    @Test
    void a_target_the_subject_does_not_have_is_refused() {
        final var result = check("""
                PROGRAM UnknownTarget
                    "NO_SUCH_THING" RETURNS 1
                    RUN
                    RESULT IS 1
                END.
                """);
        assertThat(result.passed()).isFalse();
        assertThat(result.diagnostic())
                .contains("no function or command called 'NO_SUCH_THING'");
    }

    @Test
    void supplying_a_variable_a_command_does_not_write_is_refused() {
        final var result = check("""
                PROGRAM WrongVariable
                    "COUNT ORDERS INTO _ FOR _" IS MOCKED
                    "COUNT ORDERS INTO _ FOR _" SETS "region" TO "EU"
                    RUN
                    RESULT IS 42
                END.
                """);
        assertThat(result.passed()).isFalse();
        assertThat(result.diagnostic())
                .contains("has no variable called 'region'")
                .contains("It writes total");
    }

    @Test
    void supplying_for_a_command_that_is_not_mocked_is_refused() {
        final var result = check("""
                PROGRAM NotMocked
                    "COUNT ORDERS INTO _ FOR _" SETS "total" TO 42
                    RUN
                    RESULT IS 42
                END.
                """);
        assertThat(result.passed()).isFalse();
        assertThat(result.diagnostic())
                .contains("is not mocked here, so its own handler writes 'total'");
    }

    @Test
    void supplying_for_a_function_is_refused() {
        final var result = check("""
                PROGRAM FunctionCannotWrite
                    "LOAD_ORDER" RETURNS "o1"
                    "LOAD_ORDER" SETS "total" TO 42
                    RUN
                    RESULT IS 42
                END.
                """);
        assertThat(result.passed()).isFalse();
        assertThat(result.diagnostic()).contains("is a function, not a command");
    }

    @Test
    void a_test_that_never_runs_the_subject_is_refused() {
        final var result = check("""
                PROGRAM NoAct
                    "COUNT ORDERS INTO _ FOR _" IS MOCKED
                    "COUNT ORDERS INTO _ FOR _" SETS "total" TO 1
                END.
                """);
        assertThat(result.passed()).isFalse();
        assertThat(result.diagnostic()).contains("never runs the subject");
    }

    @Test
    void a_parameter_the_subject_does_not_take_is_refused() {
        final var result = check("""
                PROGRAM WrongParameter
                    "COUNT ORDERS INTO _ FOR _" IS MOCKED
                    "COUNT ORDERS INTO _ FOR _" SETS "total" TO 1
                    ARGUMENT "orderid" IS 42
                    RUN
                    RESULT IS 1
                END.
                """);
        assertThat(result.passed()).isFalse();
        assertThat(result.diagnostic()).contains("the subject has no parameter called 'orderid'");
    }

    @Test
    void a_mock_declared_for_the_wrong_number_of_arguments_is_refused() {
        final var result = check("""
                PROGRAM WrongArity
                    "LOAD_ORDER" WITH ARGS(1, 2) RETURNS "o1"
                    "COUNT ORDERS INTO _ FOR _" IS MOCKED
                    "COUNT ORDERS INTO _ FOR _" SETS "total" TO 1
                    RUN
                    RESULT IS 1
                END.
                """);
        assertThat(result.passed()).isFalse();
        assertThat(result.diagnostic())
                .contains("LOAD_ORDER takes 1 argument(s) but was given 2");
    }

    @Test
    void a_mock_answering_the_wrong_type_is_refused() {
        final var result = check("""
                PROGRAM WrongResultType
                    "ORDER_TOTAL" RETURNS "not a number"
                    "COUNT ORDERS INTO _ FOR _" IS MOCKED
                    "COUNT ORDERS INTO _ FOR _" SETS "total" TO 1
                    RUN
                    RESULT IS 1
                END.
                """);
        assertThat(result.passed()).isFalse();
        assertThat(result.diagnostic())
                .contains("'ORDER_TOTAL' returns DECIMAL, but this answers with STRING");
    }

    /** The token convention: a STRING where an opaque value belongs is a name, not a mismatch. */
    @Test
    void answering_an_opaque_function_with_a_token_name_is_accepted() {
        final var result = check("""
                PROGRAM TokenAnswer
                    "LOAD_ORDER" RETURNS "o1"
                    "COUNT ORDERS INTO _ FOR _" IS MOCKED
                    "COUNT ORDERS INTO _ FOR _" SETS "total" TO 1
                    RUN
                    RESULT IS 1
                END.
                """);
        assertThat(result.passed()).as("%s", result.diagnostic()).isTrue();
    }

    @Test
    void a_widening_answer_is_accepted_as_it_would_be_anywhere_else() {
        final var result = check("""
                PROGRAM WideningAnswer
                    "ORDER_TOTAL" RETURNS 5
                    "COUNT ORDERS INTO _ FOR _" IS MOCKED
                    "COUNT ORDERS INTO _ FOR _" SETS "total" TO 1
                    RUN
                    RESULT IS 1
                END.
                """);
        assertThat(result.passed()).as("%s", result.diagnostic()).isTrue();
    }

    /** A pre-tested loop may run no times, so nothing inside it is guaranteed. */
    @Test
    void a_supply_inside_a_loop_that_might_not_run_is_not_a_supply() {
        final var result = check("""
                PROGRAM SuppliedInALoop
                    DECLARE i INTEGER
                    DECLARE none[0] INTEGER
                    "COUNT ORDERS INTO _ FOR _" IS MOCKED
                    FOR i = 1 TO LENGTH(none)
                        "COUNT ORDERS INTO _ FOR _" SETS "total" TO 42
                    END FOR
                    RUN
                    RESULT IS 42
                END.
                """);
        assertThat(result.passed()).isFalse();
        assertThat(result.diagnostic()).contains("nothing supplies it on every path");
    }
}
