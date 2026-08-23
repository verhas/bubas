package javax0.bubas.analyser.expression;

import javax0.bubas.analyser.BubasLanguage;
import javax0.bubas.api.BubasException;
import javax0.bubas.api.Context;
import javax0.bubas.lexer.Lexer;
import javax0.bubas.lexer.LogicalLine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class ExpressionParserTest {

    public static final class Order {
    }

    public static final class LoadOrder {
        public Order call(Context ctx, long orderId) {
            return new Order();
        }
    }

    public static final class OrderTotal {
        public BigDecimal call(Context ctx, Order order) {
            return BigDecimal.ONE;
        }
    }

    public static final class CountOrders {
        public long call(Context ctx) {
            return 0;
        }
    }

    public static final class LogEvent {
        public void call(Context ctx, String message) {
        }
    }

    private static final BubasLanguage LANGUAGE = BubasLanguage.builder()
            .defineOpaqueType("Order", Order.class)
            .defineFunction("LOAD_ORDER", LoadOrder.class)
            .defineFunction("ORDER_TOTAL", OrderTotal.class)
            .defineFunction("COUNT_ORDERS", CountOrders.class)
            .defineFunction("LOG_EVENT", LogEvent.class)
            .seal();

    private static LogicalLine line(String source) {
        return Lexer.lex(source).stream().filter(LogicalLine::hasTokens).findFirst().orElseThrow();
    }

    private static Expression parse(String source) {
        final var l = line(source);
        return ExpressionParser.parse(l, l.tokens(), LANGUAGE);
    }

    /** Prefix notation, so precedence and associativity are visible at a glance. */
    private static String tree(Expression expression) {
        return switch (expression) {
            case Expression.Constant c -> String.valueOf(c.value());
            case Expression.Variable v -> v.token().text();
            case Expression.Indexed i -> i.token().text() + "[" + tree(i.index()) + "]";
            case Expression.Call c -> c.signature().name() + "(" + c.arguments().stream()
                    .map(ExpressionParserTest::tree).collect(Collectors.joining(" ")) + ")";
            case Expression.Unary u -> "(" + u.token().text() + " " + tree(u.operand()) + ")";
            case Expression.Binary b -> "(" + b.token().text() + " " + tree(b.left()) + " "
                    + tree(b.right()) + ")";
        };
    }

    private static String rendered(String source) {
        return tree(parse(source));
    }

    /** Parses only the first {@code count} tokens, as a matcher-delimited span would arrive. */
    private static String spanRejection(String source, int count) {
        final var l = line(source);
        return catchThrowableOfType(BubasException.class,
                () -> ExpressionParser.parse(l, l.tokens().subList(0, count), LANGUAGE))
                .getMessage();
    }

    private static String rejection(String source) {
        return catchThrowableOfType(BubasException.class, () -> parse(source)).getMessage();
    }

    @Nested
    @DisplayName("precedence and associativity")
    class Precedence {

        @Test
        void multiplication_binds_tighter_than_addition() {
            assertThat(rendered("base + tax * 2")).isEqualTo("(+ base (* tax 2))");
        }

        @Test
        void parentheses_override() {
            assertThat(rendered("(base + tax) * 2")).isEqualTo("(* (+ base tax) 2)");
        }

        @Test
        void binary_operators_are_left_associative() {
            assertThat(rendered("a - b - c")).isEqualTo("(- (- a b) c)");
            assertThat(rendered("a / b / c")).isEqualTo("(/ (/ a b) c)");
        }

        @Test
        void MOD_sits_with_multiplication() {
            assertThat(rendered("a + b MOD c")).isEqualTo("(+ a (MOD b c))");
        }

        @Test
        void comparison_is_looser_than_arithmetic() {
            assertThat(rendered("a + b < c * d")).isEqualTo("(< (+ a b) (* c d))");
        }

        @Test
        void AND_is_looser_than_comparison_and_OR_looser_still() {
            assertThat(rendered("a < b AND c > d OR e"))
                    .isEqualTo("(OR (AND (< a b) (> c d)) e)");
        }

        @Test
        void unary_binds_tighter_than_any_binary_operator() {
            assertThat(rendered("-a + b")).isEqualTo("(+ (- a) b)");
            assertThat(rendered("NOT found AND ready")).isEqualTo("(AND (NOT found) ready)");
        }

        @Test
        void unary_operators_stack() {
            assertThat(rendered("NOT NOT ready")).isEqualTo("(NOT (NOT ready))");
        }
    }

    @Nested
    @DisplayName("leaves")
    class Leaves {

        @Test
        void literals_carry_the_value_the_lexer_parsed() {
            assertThat(rendered("42")).isEqualTo("42");
            assertThat(rendered("10.50")).isEqualTo("10.50");
            assertThat(rendered("\"hi\"")).isEqualTo("hi");
            assertThat(rendered("TRUE")).isEqualTo("true");
            assertThat(rendered("FALSE")).isEqualTo("false");
        }

        @Test
        void a_negative_literal_is_unary_minus_applied_to_it() {
            // The lexer deliberately does not produce signed literals, so a-10 and a - 10 agree.
            assertThat(rendered("-10")).isEqualTo("(- 10)");
        }

        @Test
        void an_index_is_an_ordinary_expression() {
            assertThat(rendered("numbers[i + 1]")).isEqualTo("numbers[(+ i 1)]");
        }

        @Test
        void an_index_may_hold_a_call() {
            assertThat(rendered("items[COUNT_ORDERS() - 1]"))
                    .isEqualTo("items[(- COUNT_ORDERS() 1)]");
        }
    }

    @Nested
    @DisplayName("calls")
    class Calls {

        @Test
        void a_call_resolves_to_its_signature() {
            final var call = (Expression.Call) parse("LOAD_ORDER(42)");
            assertThat(call.signature().name()).isEqualTo("LOAD_ORDER");
            assertThat(call.arguments()).hasSize(1);
        }

        @Test
        void a_zero_argument_call_still_needs_parentheses() {
            assertThat(rendered("COUNT_ORDERS()")).isEqualTo("COUNT_ORDERS()");
            assertThat(rejection("COUNT_ORDERS"))
                    .contains("a call needs parentheses, even with no arguments");
        }

        @Test
        void calls_nest() {
            assertThat(rendered("ORDER_TOTAL(LOAD_ORDER(42)) * 2"))
                    .isEqualTo("(* ORDER_TOTAL(LOAD_ORDER(42)) 2)");
        }

        @Test
        void an_argument_may_be_any_expression() {
            assertThat(rendered("LOAD_ORDER(a + b * 2)")).isEqualTo("LOAD_ORDER((+ a (* b 2)))");
        }

        @Test
        void arity_is_checked_against_the_signature() {
            assertThat(rejection("LOAD_ORDER(1, 2)"))
                    .contains("LOAD_ORDER takes 1 argument(s) but was given 2")
                    .contains("LOAD_ORDER(orderId INTEGER) -> Order");
        }

        @Test
        void a_procedure_has_no_value_to_contribute() {
            assertThat(rejection("LOG_EVENT(\"hi\") + 1"))
                    .contains("LOG_EVENT returns nothing");
        }
    }

    @Nested
    @DisplayName("rejections")
    class Rejections {

        @Test
        void a_reserved_word_cannot_appear() {
            assertThat(rejection("a + Order")).contains("'Order' is reserved");
        }

        @Test
        void an_unclosed_parenthesis_is_reported() {
            assertThat(rejection("(a + b")).contains("never closed");
        }

        /**
         * The lexer rejects an unbalanced or unfinished <em>line</em> before the parser ever runs,
         * so these are reached only through the public entry point, which accepts any span. A
         * matcher-delimited span can genuinely end on an operator: given the pattern
         * {@code PAY {expression:a} VIA {var:b}} and the line {@code PAY x + VIA acct}, the
         * expression stops at the reserved {@code VIA} and leaves the {@code +} dangling.
         */
        @Test
        void a_span_ending_on_an_operator_is_reported() {
            assertThat(spanRejection("a + b", 2)).contains("ends unfinished");
        }

        @Test
        void a_span_ending_inside_an_index_is_reported() {
            assertThat(spanRejection("numbers[i]", 3)).contains("an index opened on 'numbers'");
        }

        @Test
        void a_span_ending_inside_a_group_is_reported() {
            assertThat(spanRejection("(a + b)", 4)).contains("never closed");
        }

        @Test
        void an_empty_span_is_reported() {
            assertThat(spanRejection("a", 0)).contains("an expression is missing");
        }

        @Test
        void a_diagnostic_names_the_line_and_the_position() {
            final var e = catchThrowableOfType(BubasException.class, () -> parse("a + Order"));
            assertThat(e.getLine()).isEqualTo(1);
            assertThat(e.getSourceLine()).isEqualTo("a + Order");
            assertThat(e.getMessage()).contains("at 1:5");
        }
    }
}
