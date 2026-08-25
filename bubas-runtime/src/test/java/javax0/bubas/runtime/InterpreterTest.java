package javax0.bubas.runtime;

import javax0.bubas.analyser.BubasLanguage;
import javax0.bubas.api.BubasException;
import javax0.bubas.api.BubasArray;
import javax0.bubas.api.BubasType;
import javax0.bubas.api.Context;
import java.util.Set;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import javax0.bubas.api.StatementContext;
import javax0.bubas.api.ExpressionArg;
import javax0.bubas.api.VariableArg;
import javax0.bubas.api.BubasCallInterceptor;
import javax0.bubas.api.Value;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
                .install(Standard::register)
                .defineOpaqueType("Order", Order.class)
                .defineFunction("LOAD_ORDER", LoadOrder.class)
                .defineFunction("ORDER_TOTAL", OrderTotal.class)
                .defineFunction("LOG_EVENT", LogEvent.class);
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
    @DisplayName("call interception")
    class Interception {

        /** A command that assigns: the reason an interceptor gets the handler's own arguments. */
        public static final class LoadInto {
            public void call(StatementContext ctx, VariableArg target, ExpressionArg id) {
                target.set(new Order(BigDecimal.TEN));
            }
        }

        public static final class Approve {
            public void call(StatementContext ctx, ExpressionArg target) {
                ctx.log("REAL", "approved");
            }
        }

        static final String LOAD_INTO = "LOAD {new > identifier/Order:target > initialized} FOR {expression:id}";
        static final String APPROVE = "APPROVE {expression/Order:target}";

        private static final BubasLanguage LANG = BubasLanguage.builder()
                .install(Standard::register)
                .defineOpaqueType("Order", Order.class)
                .defineFunction("LOAD_ORDER", LoadOrder.class)
                .defineFunction("ORDER_TOTAL", OrderTotal.class)
                .defineFunction("LOG_EVENT", LogEvent.class)
                .defineStatement(LOAD_INTO, LoadInto.class)
                .defineStatement(APPROVE, Approve.class)
                .registerService(Orders.class, id -> new Order(new BigDecimal("5")))
                .seal();

        /** Records every call and answers from a table, which is all a mock ever does. */
        static class Recorder implements BubasCallInterceptor {
            final List<String> calls = new ArrayList<>();
            final Map<String, Value> functions = new LinkedHashMap<>();
            final Set<String> commands = new LinkedHashSet<>();

            @Override
            public boolean interceptsFunction(String name) {
                return functions.containsKey(name);
            }

            @Override
            public Value onFunction(String name, List<Value> arguments) {
                calls.add(name + arguments.stream()
                        .map(v -> String.valueOf(v.as(Object.class)))
                        .toList());
                return functions.get(name);
            }

            @Override
            public boolean interceptsCommand(String pattern) {
                return commands.contains(pattern);
            }

            @Override
            public void onCommand(String pattern, StatementContext context,
                                  Map<String, Object> arguments) {
                calls.add(pattern);
                // Approach three: an opaque target is written automatically by the framework.
                arguments.forEach((name, argument) -> {
                    if (argument instanceof VariableArg variable
                            && variable.type() instanceof BubasType.Opaque) {
                        variable.set(new Order(BigDecimal.ZERO));
                    }
                });
            }
        }

        private static Value decimal(String value) {
            return new RuntimeValue(BubasType.DECIMAL, new BigDecimal(value));
        }

        @Test
        void an_intercepted_function_answers_instead_of_its_implementation() {
            final var recorder = new Recorder();
            recorder.functions.put("ORDER_TOTAL", decimal("99.00"));
            final var result = Interpreter.of(LANG.compile("""
                    PROGRAM T RETURNS DECIMAL
                        DECLARE purchase Order
                        purchase = LOAD_ORDER(1)
                        RETURN ORDER_TOTAL(purchase)
                    END.
                    """)).intercept(recorder).run();
            assertThat(result.asDecimal()).isEqualByComparingTo("99.00");
        }

        @Test
        void an_unintercepted_function_still_runs_for_real() {
            final var recorder = new Recorder();
            final var result = Interpreter.of(LANG.compile("""
                    PROGRAM T RETURNS DECIMAL
                        DECLARE purchase Order
                        purchase = LOAD_ORDER(1)
                        RETURN ORDER_TOTAL(purchase)
                    END.
                    """)).intercept(recorder).run();
            assertThat(result.asDecimal()).isEqualByComparingTo("5");
            assertThat(recorder.calls).isEmpty();
        }

        @Test
        void arguments_reach_the_interceptor_evaluated_and_in_source_order() {
            final var recorder = new Recorder();
            recorder.functions.put("ORDER_TOTAL", decimal("1"));
            Interpreter.of(LANG.compile("""
                    PROGRAM T RETURNS DECIMAL
                        DECLARE purchase Order
                        purchase = LOAD_ORDER(1)
                        RETURN ORDER_TOTAL(purchase)
                    END.
                    """)).intercept(recorder).run();
            assertThat(recorder.calls).hasSize(1);
            assertThat(recorder.calls.getFirst()).startsWith("ORDER_TOTAL[");
        }

        @Test
        void an_intercepted_command_does_not_run_its_handler() {
            final var recorder = new Recorder();
            recorder.commands.add(APPROVE);
            final var logged = new ArrayList<String>();
            Interpreter.of(LANG.compile("""
                    PROGRAM T
                        DECLARE purchase Order
                        purchase = LOAD_ORDER(1)
                        APPROVE purchase
                    END.
                    """)).intercept(recorder).logger((l, m) -> logged.add(l)).run();
            assertThat(recorder.calls).containsExactly(APPROVE);
            assertThat(logged).doesNotContain("REAL");
        }

        /**
         * The wrinkle that makes commands different from functions: the pattern declares the
         * variable, so a mock that only records leaves the script reading an unassigned slot.
         */
        @Test
        void an_intercepted_command_writes_what_its_pattern_declares() {
            final var recorder = new Recorder();
            recorder.commands.add(LOAD_INTO);
            recorder.functions.put("ORDER_TOTAL", decimal("7"));
            final var result = Interpreter.of(LANG.compile("""
                    PROGRAM T RETURNS DECIMAL
                        LOAD purchase FOR 42
                        RETURN ORDER_TOTAL(purchase)
                    END.
                    """)).intercept(recorder).run();
            assertThat(result.asDecimal()).isEqualByComparingTo("7");
            // The second call proves the write happened: an unwritten slot would arrive as null.
            assertThat(recorder.calls).hasSize(2);
            assertThat(recorder.calls.getFirst()).isEqualTo(LOAD_INTO);
            assertThat(recorder.calls.getLast()).startsWith("ORDER_TOTAL[")
                    .doesNotContain("null");
        }

        @Test
        void a_non_void_function_whose_mock_supplies_nothing_says_so() {
            final var recorder = new Recorder() {
                @Override
                public boolean interceptsFunction(String name) {
                    return "ORDER_TOTAL".equals(name);
                }

                @Override
                public Value onFunction(String name, List<Value> arguments) {
                    return null;
                }
            };
            assertThat(catchThrowableOfType(BubasException.class, () -> Interpreter.of(LANG.compile("""
                    PROGRAM T RETURNS DECIMAL
                        DECLARE purchase Order
                        purchase = LOAD_ORDER(1)
                        RETURN ORDER_TOTAL(purchase)
                    END.
                    """)).intercept(recorder).run()).getMessage())
                    .contains("ORDER_TOTAL returns DECIMAL")
                    .contains("the interceptor supplied no value");
        }

        @Test
        void without_an_interceptor_everything_runs_for_real() {
            final var result = Interpreter.of(LANG.compile("""
                    PROGRAM T RETURNS DECIMAL
                        DECLARE purchase Order
                        purchase = LOAD_ORDER(1)
                        RETURN ORDER_TOTAL(purchase)
                    END.
                    """)).run();
            assertThat(result.asDecimal()).isEqualByComparingTo("5");
        }
    }

    @Nested
    @DisplayName("the ANY wildcard parameter")
    class AnyParameter {

        /**
         * The idiom for a wildcard parameter: ask the value what it is, then read it accordingly.
         * {@code as(Object.class)} is the shortcut when any rendering will do.
         */
        public static final class Describe {
            public String call(Context ctx, Value value) {
                return value.type() + "=" + String.valueOf(value.as(Object.class));
            }
        }

        /** Variadic and wildcard together: every element arrives boxed with its own type. */
        public static final class Render {
            public String call(Context ctx, Value... parts) {
                final var out = new StringBuilder();
                for (final var part : parts) {
                    out.append(part.type()).append(':')
                            .append(String.valueOf(part.as(Object.class))).append(';');
                }
                return out.toString();
            }
        }

        public static final class Tagged {
            public String call(Context ctx, String tag, Value value) {
                return tag + "/" + value.type();
            }
        }

        /** An array reaching a wildcard arrives as its raw Java array, not wrapped. */
        public static final class SizeOf {
            public long call(Context ctx, Value value) {
                return value.as(long[].class).length;
            }
        }

        private static final BubasLanguage LANG = BubasLanguage.builder()
                .install(Standard::register)
                .defineOpaqueType("Order", Order.class)
                .defineFunction("LOAD_ORDER", LoadOrder.class)
                .defineFunction("DESCRIBE", Describe.class)
                .defineFunction("RENDER", Render.class)
                .defineFunction("TAGGED", Tagged.class)
                .registerService(Orders.class, id -> new Order(BigDecimal.ONE))
                .seal();

        private static String run(String expression) {
            return Interpreter.of(LANG.compile(
                    "PROGRAM T RETURNS STRING\n    RETURN " + expression + "\nEND.\n"))
                    .run().asString();
        }

        @Test
        void a_wildcard_parameter_carries_the_type_it_was_given() {
            assertThat(run("DESCRIBE(42)")).isEqualTo("INTEGER=42");
            assertThat(run("DESCRIBE(\"text\")")).isEqualTo("STRING=text");
            assertThat(run("DESCRIBE(TRUE)")).isEqualTo("BOOLEAN=true");
            assertThat(run("DESCRIBE(1.50)")).isEqualTo("DECIMAL=1.50");
        }

        @Test
        void an_expression_is_evaluated_before_it_is_boxed() {
            assertThat(run("DESCRIBE(2 + 3 * 4)")).isEqualTo("INTEGER=14");
        }

        @Test
        void every_variadic_wildcard_element_keeps_its_own_type() {
            assertThat(run("RENDER(1, \"two\", TRUE)"))
                    .isEqualTo("INTEGER:1;STRING:two;BOOLEAN:true;");
        }

        @Test
        void a_variadic_wildcard_takes_no_arguments_too() {
            assertThat(run("RENDER()")).isEmpty();
        }

        @Test
        void a_wildcard_follows_concrete_parameters() {
            assertThat(run("TAGGED(\"t\", 9)")).isEqualTo("t/INTEGER");
        }

        @Test
        void an_opaque_value_reaches_a_wildcard_parameter_with_its_registered_type() {
            assertThat(run("DESCRIBE(LOAD_ORDER(1))")).startsWith("Order=");
        }

        @Test
        void an_array_reaches_a_wildcard_parameter_as_its_raw_java_array() {
            final var language = BubasLanguage.builder()
                    .install(Standard::register)
                    .defineFunction("SIZE_OF", SizeOf.class)
                    .seal();
            final var source = """
                    PROGRAM T RETURNS INTEGER
                        DECLARE numbers[3] INTEGER
                        RETURN SIZE_OF(numbers)
                    END.
                    """;
            assertThat(Interpreter.of(language.compile(source)).run().asLong()).isEqualTo(3L);
        }
    }

    @Nested
    @DisplayName("variadic functions")
    class Varargs {

        public static final class Join {
            public String call(Context ctx, String... parts) {
                return String.join("-", parts);
            }
        }

        public static final class SumOf {
            public long call(Context ctx, long... numbers) {
                long total = 0;
                for (final var n : numbers) {
                    total += n;
                }
                return total;
            }
        }

        public static final class Tagged {
            public String call(Context ctx, String tag, long... numbers) {
                return tag + ":" + numbers.length;
            }
        }

        public static final class CountOrders {
            public long call(Context ctx, Order... orders) {
                return orders.length;
            }
        }

        private static final BubasLanguage LANG = BubasLanguage.builder()
                .install(Standard::register)
                .defineOpaqueType("Order", Order.class)
                .defineFunction("LOAD_ORDER", LoadOrder.class)
                .defineFunction("JOIN", Join.class)
                .defineFunction("SUM_OF", SumOf.class)
                .defineFunction("TAGGED", Tagged.class)
                .defineFunction("COUNT_ORDERS", CountOrders.class)
                .registerService(Orders.class, id -> new Order(BigDecimal.ONE))
                .seal();

        private static Value run(String returns, String expression) {
            return Interpreter.of(LANG.compile(
                    "PROGRAM T RETURNS " + returns + "\n    RETURN " + expression + "\nEND.\n"))
                    .run();
        }

        @Test
        void several_arguments_are_packed_into_the_array() {
            assertThat(run("STRING", "JOIN(\"a\", \"b\", \"c\")").asString()).isEqualTo("a-b-c");
        }

        @Test
        void one_argument() {
            assertThat(run("STRING", "JOIN(\"only\")").asString()).isEqualTo("only");
        }

        @Test
        void no_arguments_gives_an_empty_array_rather_than_null() {
            assertThat(run("STRING", "JOIN()").asString()).isEmpty();
        }

        /** A primitive component type: the packed array must be long[], not Long[]. */
        @Test
        void a_primitive_element_type_is_unboxed_into_its_own_array() {
            assertThat(run("INTEGER", "SUM_OF(1, 2, 3, 4)").asLong()).isEqualTo(10L);
            assertThat(run("INTEGER", "SUM_OF()").asLong()).isZero();
        }

        @Test
        void fixed_arguments_are_passed_before_the_packed_ones() {
            assertThat(run("STRING", "TAGGED(\"t\", 1, 2)").asString()).isEqualTo("t:2");
            assertThat(run("STRING", "TAGGED(\"t\")").asString()).isEqualTo("t:0");
        }

        /** An opaque element type: the array's component is the registered Java class. */
        @Test
        void opaque_values_pack_into_an_array_of_their_java_type() {
            assertThat(run("INTEGER", "COUNT_ORDERS(LOAD_ORDER(1), LOAD_ORDER(2))").asLong())
                    .isEqualTo(2L);
        }

        @Test
        void an_expression_argument_is_evaluated_before_packing() {
            assertThat(run("INTEGER", "SUM_OF(1 + 1, 2 * 3)").asLong()).isEqualTo(8L);
        }
    }

    @Nested
    @DisplayName("services registered on the language")
    class LanguageServices {

        private static BubasLanguage languageServing(Orders orders) {
            return BubasLanguage.builder()
                    .install(Standard::register)
                    .defineOpaqueType("Order", Order.class)
                    .defineFunction("LOAD_ORDER", LoadOrder.class)
                    .defineFunction("ORDER_TOTAL", OrderTotal.class)
                    .registerService(Orders.class, orders)
                    .seal();
        }

        private static final String BODY = """
                PROGRAM P RETURNS DECIMAL
                    DECLARE purchase Order
                    purchase = LOAD_ORDER(7)
                    RETURN ORDER_TOTAL(purchase)
                END.
                """;

        @Test
        void a_language_service_reaches_a_run_that_registered_nothing() {
            final var program = languageServing(id -> new Order(BigDecimal.TEN)).compile(BODY);
            assertThat(Interpreter.of(program).run().asDecimal())
                    .isEqualByComparingTo(BigDecimal.TEN);
        }

        @Test
        void every_interpreter_of_one_language_shares_it() {
            final var program = languageServing(id -> new Order(BigDecimal.TEN)).compile(BODY);
            assertThat(Interpreter.of(program).run().asDecimal())
                    .isEqualByComparingTo(BigDecimal.TEN);
            assertThat(Interpreter.of(program).run().asDecimal())
                    .isEqualByComparingTo(BigDecimal.TEN);
        }

        /**
         * The interpreter seeds from the language, and the language's maps are immutable — so this
         * is also the test that the seeding copies rather than aliases them.
         */
        @Test
        void a_run_may_override_the_language_service() {
            final var program = languageServing(id -> new Order(BigDecimal.TEN)).compile(BODY);
            assertThat(Interpreter.of(program)
                    .registerService(Orders.class, id -> new Order(BigDecimal.ONE))
                    .run().asDecimal())
                    .isEqualByComparingTo(BigDecimal.ONE);
        }

        @Test
        void an_override_lasts_only_for_the_run_that_made_it() {
            final var program = languageServing(id -> new Order(BigDecimal.TEN)).compile(BODY);
            Interpreter.of(program)
                    .registerService(Orders.class, id -> new Order(BigDecimal.ONE))
                    .run();
            assertThat(Interpreter.of(program).run().asDecimal())
                    .isEqualByComparingTo(BigDecimal.TEN);
        }

        @Test
        void a_qualifier_separates_two_services_of_one_type() {
            final var language = BubasLanguage.builder()
                    .install(Standard::register)
                    .defineOpaqueType("Order", Order.class)
                    .defineFunction("LOAD_ORDER", LoadOrder.class)
                    .defineFunction("ORDER_TOTAL", OrderTotal.class)
                    .registerService(Orders.class, "read", id -> new Order(BigDecimal.ONE))
                    .registerService(Orders.class, id -> new Order(BigDecimal.TEN))
                    .seal();
            assertThat(language.services().get(Orders.class)).containsOnlyKeys("", "read");
            assertThat(Interpreter.of(language.compile(BODY)).run().asDecimal())
                    .isEqualByComparingTo(BigDecimal.TEN);
        }

        @Test
        void the_exposed_map_is_immutable() {
            final var language = languageServing(id -> new Order(BigDecimal.TEN));
            assertThatThrownBy(() -> language.services().get(Orders.class).put("x", null))
                    .isInstanceOf(UnsupportedOperationException.class);
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
