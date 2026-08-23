package javax0.bubas.runtime;

import javax0.bubas.analyser.BubasLanguage;
import javax0.bubas.api.BubasException;
import javax0.bubas.api.Context;
import javax0.bubas.support.Standard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class InterpreterTest {

    public static final class Order {
        final BigDecimal total;

        Order(BigDecimal total) {
            this.total = total;
        }
    }

    public interface Orders {
        Order load(long id);
    }

    public static final class LoadOrder {
        public Order call(Context ctx, long orderId) {
            return ctx.service(Orders.class).load(orderId);
        }
    }

    public static final class OrderTotal {
        public BigDecimal call(Context ctx, Order order) {
            return order.total;
        }
    }

    public static final class LogEvent {
        public void call(Context ctx, String level, String message) {
            ctx.log(level, message);
        }
    }

    private static final BubasLanguage LANGUAGE = language();

    private static BubasLanguage language() {
        final var builder = BubasLanguage.builder()
                .defineOpaqueType("Order", Order.class)
                .defineFunction("LOAD_ORDER", LoadOrder.class)
                .defineFunction("ORDER_TOTAL", OrderTotal.class)
                .defineFunction("LOG_EVENT", LogEvent.class);
        Standard.STATEMENTS.forEach(builder::defineStatement);
        return builder.seal();
    }

    private final List<String> logged = new ArrayList<>();

    private Interpreter interpreter(String body) {
        return Interpreter.of(LANGUAGE.compile("PROGRAM P\n" + body + "\nEND."))
                .logger((level, message) -> logged.add(level + ": " + message));
    }

    /** Runs a body that returns nothing, and yields whatever it logged. */
    private List<String> run(String body) {
        interpreter(body).run();
        return logged;
    }

    /** Runs a body wrapped in a program returning INTEGER. */
    private long value(String body) {
        return Interpreter.of(LANGUAGE.compile("PROGRAM P RETURNS INTEGER\n" + body + "\nEND."))
                .logger((level, message) -> logged.add(level + ": " + message))
                .run().asLong();
    }

    private String rejection(String body) {
        return catchThrowableOfType(BubasException.class, () -> run(body)).getMessage();
    }

    @Nested
    @DisplayName("expressions")
    class Expressions {

        @Test
        void integer_arithmetic_evaluates() {
            assertThat(value("DECLARE n INTEGER\nn = 2 + 3 * 4\nRETURN n")).isEqualTo(14);
            assertThat(value("RETURN 7 / 2")).isEqualTo(3);
            assertThat(value("RETURN -7 / 2")).isEqualTo(-3);
            assertThat(value("RETURN -7 MOD 2")).isEqualTo(-1);
        }

        @Test
        void integer_overflow_is_an_error_not_a_wraparound() {
            assertThat(rejection("DECLARE n INTEGER\nn = 9223372036854775807 + 1\n"
                    + "LOG_EVENT \"INFO\", \"\" + n")).isEqualTo("integer overflow");
        }

        @Test
        void division_by_zero_is_reported() {
            assertThat(rejection("DECLARE n INTEGER\nn = 1 / 0\nLOG_EVENT \"INFO\", \"\" + n"))
                    .isEqualTo("division by zero");
        }

        @Test
        void decimal_addition_is_exact_and_keeps_scale() {
            assertThat(run("DECLARE d DECIMAL\nd = 0.1 + 0.2\nLOG_EVENT \"INFO\", \"\" + d"))
                    .containsExactly("INFO: 0.3");
        }

        @Test
        void decimal_division_uses_the_math_context() {
            final var program = LANGUAGE.compile("""
                    PROGRAM P
                        DECLARE d DECIMAL
                        d = 1.0 / 3.0
                        LOG_EVENT "INFO", "" + d
                    END.""");
            final var seen = new ArrayList<String>();
            Interpreter.of(program)
                    .mathContext(new MathContext(5, RoundingMode.HALF_EVEN))
                    .logger((level, message) -> seen.add(message))
                    .run();
            assertThat(seen).containsExactly("0.33333");
        }

        @Test
        void an_integer_widens_where_a_decimal_is_wanted() {
            assertThat(run("DECLARE d DECIMAL\nd = 5\nLOG_EVENT \"INFO\", \"\" + d"))
                    .containsExactly("INFO: 5");
        }

        @Test
        void text_renders_as_the_language_promises() {
            assertThat(run("""
                    DECLARE d DECIMAL
                    DECLARE b BOOLEAN
                    d = 10.50
                    b = TRUE
                    LOG_EVENT "INFO", "d=" + d + " b=" + b + " n=" + 42
                    """)).containsExactly("INFO: d=10.50 b=TRUE n=42");
        }

        @Test
        void decimals_compare_by_value_not_by_scale() {
            assertThat(value("""
                    DECLARE a DECIMAL
                    DECLARE b DECIMAL
                    a = 2.0
                    b = 2.00
                    IF a = b THEN
                        RETURN 1
                    END IF
                    RETURN 0""")).isEqualTo(1);
        }

        @Test
        void AND_and_OR_short_circuit() {
            // The second operand would divide by zero if it were evaluated.
            assertThat(value("DECLARE n INTEGER\nn = 0\nIF n <> 0 AND 1 / n > 0 THEN\n"
                    + "    RETURN 1\nEND IF\nRETURN 0")).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("control flow")
    class Control {

        @Test
        void an_if_chain_takes_the_first_matching_arm() {
            assertThat(value("""
                    DECLARE n INTEGER
                    n = 5
                    IF n > 10 THEN
                        RETURN 1
                    ELSEIF n > 3 THEN
                        RETURN 2
                    ELSE
                        RETURN 3
                    END IF""")).isEqualTo(2);
        }

        @Test
        void a_pre_test_loop_may_never_run() {
            assertThat(value("""
                    DECLARE n INTEGER
                    n = 0
                    DO WHILE FALSE
                        n = n + 1
                    END DO
                    RETURN n""")).isEqualTo(0);
        }

        @Test
        void a_post_test_loop_always_runs_once() {
            assertThat(value("""
                    DECLARE n INTEGER
                    n = 0
                    DO
                        n = n + 1
                    END DO UNTIL TRUE
                    RETURN n""")).isEqualTo(1);
        }

        @Test
        void a_for_loop_counts_and_leaves_the_first_failing_value_behind() {
            assertThat(value("""
                    DECLARE i INTEGER
                    DECLARE sum INTEGER
                    sum = 0
                    FOR i = 0 TO 4
                        sum = sum + i
                    END FOR
                    RETURN sum * 100 + i""")).isEqualTo(1005);
        }

        @Test
        void a_for_loop_may_count_down() {
            assertThat(value("""
                    DECLARE i INTEGER
                    DECLARE sum INTEGER
                    sum = 0
                    FOR i = 10 TO 0 STEP -2
                        sum = sum + 1
                    END FOR
                    RETURN sum""")).isEqualTo(6);
        }

        @Test
        void EXIT_leaves_the_loop_lowering_chose() {
            assertThat(value("""
                    DECLARE i INTEGER
                    DECLARE hit INTEGER
                    hit = -1
                    FOR i = 0 TO 100
                        DO WHILE TRUE
                            EXIT FOR
                        END DO
                    END FOR
                    hit = i
                    RETURN hit""")).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("arrays, calls and services")
    class Vocabulary {

        @Test
        void an_array_is_declared_indexed_and_written() {
            assertThat(value("""
                    DECLARE a[3] INTEGER
                    DECLARE i INTEGER
                    a[0] = 10
                    a[1] = 20
                    a[2] = 30
                    DECLARE sum INTEGER
                    sum = 0
                    FOR i = 0 TO 2
                        sum = sum + a[i]
                    END FOR
                    RETURN sum""")).isEqualTo(60);
        }

        @Test
        void an_index_outside_the_array_is_reported() {
            assertThat(rejection("DECLARE a[2] INTEGER\na[5] = 1\nLOG_EVENT \"INFO\", \"\" + a[0]"))
                    .isEqualTo("index 5 is outside an array of 2");
        }

        @Test
        void a_string_array_starts_filled_with_empty_strings() {
            assertThat(run("DECLARE names[2] STRING\nLOG_EVENT \"INFO\", \"[\" + names[0] + \"]\""))
                    .containsExactly("INFO: []");
        }

        @Test
        void a_function_reaches_its_dependency_through_a_service() {
            final var program = LANGUAGE.compile("""
                    PROGRAM P(orderId INTEGER) RETURNS DECIMAL
                        DECLARE purchase Order
                        purchase = LOAD_ORDER(orderId)
                        RETURN ORDER_TOTAL(purchase)
                    END.""");
            final var result = Interpreter.of(program)
                    .argument("orderId", 7L)
                    .registerService(Orders.class, id -> new Order(new BigDecimal("12.34")))
                    .run();
            assertThat(result.asDecimal()).isEqualByComparingTo("12.34");
        }

        @Test
        void a_missing_service_is_reported_against_the_line_that_needed_it() {
            final var program = LANGUAGE.compile("""
                    PROGRAM P(orderId INTEGER) RETURNS DECIMAL
                        DECLARE purchase Order
                        purchase = LOAD_ORDER(orderId)
                        RETURN ORDER_TOTAL(purchase)
                    END.""");
            final var thrown = catchThrowableOfType(BubasException.class,
                    () -> Interpreter.of(program).argument("orderId", 1L).run());
            assertThat(thrown.getMessage()).contains("no Orders service is registered");
            assertThat(thrown.getLine()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("the interpreter itself")
    class Lifecycle {

        @Test
        void a_parameter_must_be_supplied_and_of_the_right_type() {
            final var program = LANGUAGE.compile("PROGRAM P(n INTEGER) RETURNS INTEGER\n"
                    + "RETURN n\nEND.");
            assertThat(catchThrowableOfType(BubasException.class,
                    () -> Interpreter.of(program).run()).getMessage())
                    .contains("needs an argument for 'n'");
            assertThat(catchThrowableOfType(BubasException.class,
                    () -> Interpreter.of(program).argument("n", "text")).getMessage())
                    .contains("'n' is INTEGER, so it cannot be given a String");
            assertThat(catchThrowableOfType(BubasException.class,
                    () -> Interpreter.of(program).argument("ghost", 1L)).getMessage())
                    .contains("has no parameter named 'ghost'");
        }

        @Test
        void an_interpreter_runs_once() {
            final var interpreter = interpreter("LOG_EVENT \"INFO\", \"hello\"");
            interpreter.run();
            assertThat(catchThrowableOfType(BubasException.class, interpreter::run).getMessage())
                    .contains("runs once");
        }

        @Test
        void a_program_is_reusable_across_runs() {
            final var program = LANGUAGE.compile("PROGRAM P RETURNS INTEGER\nRETURN 42\nEND.");
            assertThat(Interpreter.of(program).run().asLong()).isEqualTo(42);
            assertThat(Interpreter.of(program).run().asLong()).isEqualTo(42);
        }
    }
}
