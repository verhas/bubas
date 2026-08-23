package javax0.bubas.analyser.type;

import javax0.bubas.analyser.BubasLanguage;
import javax0.bubas.analyser.flow.FlowAnalyser;
import javax0.bubas.analyser.statement.StatementParser;
import javax0.bubas.support.Standard;
import javax0.bubas.api.BubasException;
import javax0.bubas.api.Context;
import javax0.bubas.api.ExpressionArg;
import javax0.bubas.api.StatementContext;
import javax0.bubas.api.VariableArg;
import javax0.bubas.lexer.Lexer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class TypeCheckerTest {

    public static final class Order {
    }

    public static final class LogEvent {
        public void call(Context ctx, String message) {
        }
    }

    public static final class Show {
        public void call(StatementContext ctx, VariableArg value) {
        }
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

    public static final class Scale {
        public void call(StatementContext ctx, ExpressionArg by) {
        }
    }

    /** A builder with the standard declaration and assignment statements already installed. */
    private static BubasLanguage.Builder standard() {
        final var builder = BubasLanguage.builder();
        Standard.STATEMENTS.forEach(builder::defineStatement);
        return builder;
    }

    private static final BubasLanguage LANGUAGE = standard()
            .defineOpaqueType("Order", Order.class)
            .defineFunction("LOAD_ORDER", LoadOrder.class)
            .defineFunction("ORDER_TOTAL", OrderTotal.class)
            .defineFunction("LOG_EVENT", LogEvent.class)
            .defineStatement("SHOW {initialized > var:value}", Show.class)
            .defineStatement("SCALE BY {expression/NUMBER:by}", Scale.class)
            .seal();

    /** The rejection message, or null when the body type-checks. */
    private static String reject(String body) {
        final var source = "PROGRAM P\n" + body + "\nEND.";
        final var thrown = catchThrowableOfType(BubasException.class, () -> {
            final var program = StatementParser.parse(Lexer.lex(source), LANGUAGE);
            TypeChecker.check(program, LANGUAGE, FlowAnalyser.check(program, LANGUAGE));
        });
        return thrown == null ? null : thrown.getMessage();
    }

    private static final String INT = "DECLARE n INTEGER\nn = 1\n";
    private static final String DEC = "DECLARE d DECIMAL\nd = 1.0\n";
    private static final String STR = "DECLARE s STRING\ns = \"x\"\n";
    private static final String BOOL = "DECLARE b BOOLEAN\nb = TRUE\n";

    @Nested
    @DisplayName("arithmetic")
    class Arithmetic {

        @Test
        void an_integer_widens_to_a_decimal() {
            assertThat(reject(INT + DEC + "d = n + d\nSHOW d\nSHOW n")).isNull();
        }

        @Test
        void a_decimal_does_not_narrow_to_an_integer() {
            assertThat(reject(INT + DEC + "n = d\nSHOW d\nSHOW n"))
                    .contains("must be INTEGER, but this one is DECIMAL");
        }

        @Test
        void arithmetic_needs_numbers() {
            assertThat(reject(INT + BOOL + "n = n + b\nSHOW n\nSHOW b"))
                    .contains("+ needs numbers, not INTEGER and BOOLEAN");
        }

        @Test
        void MOD_is_defined_for_integers_only() {
            assertThat(reject(INT + DEC + "d = d MOD 2\nSHOW d\nSHOW n"))
                    .contains("MOD is defined for INTEGER only");
        }
    }

    @Nested
    @DisplayName("string concatenation")
    class Concatenation {

        @Test
        void a_string_on_the_left_coerces_the_right() {
            assertThat(reject(INT + STR + "s = \"n=\" + n\nSHOW s\nSHOW n")).isNull();
        }

        @Test
        void a_string_on_the_right_does_not_coerce_the_left() {
            assertThat(reject(INT + STR + "s = n + \"x\"\nSHOW s\nSHOW n"))
                    .contains("a STRING can only be added to a STRING");
        }

        @Test
        void the_empty_string_idiom_works_because_plus_groups_left() {
            assertThat(reject(INT + STR + "s = \"\" + n + \"x\"\nSHOW s\nSHOW n")).isNull();
        }

        @Test
        void an_opaque_value_has_no_text_form() {
            assertThat(reject(STR + "DECLARE o Order\no = LOAD_ORDER(1)\ns = \"o=\" + o\n"
                    + "SHOW s\nSHOW o")).contains("has no text form");
        }
    }

    @Nested
    @DisplayName("comparison")
    class Comparison {

        @Test
        void numbers_and_strings_admit_every_operator() {
            assertThat(reject(INT + BOOL + "b = n < 2\nSHOW b\nSHOW n")).isNull();
            assertThat(reject(STR + BOOL + "b = s < \"z\"\nSHOW b\nSHOW s")).isNull();
        }

        @Test
        void booleans_compare_only_for_equality() {
            assertThat(reject(BOOL + "DECLARE c BOOLEAN\nc = b = TRUE\nSHOW c\nSHOW b")).isNull();
            assertThat(reject(BOOL + "DECLARE c BOOLEAN\nc = b < TRUE\nSHOW c\nSHOW b"))
                    .contains("BOOLEAN values compare only with = and <>");
        }

        @Test
        void an_opaque_value_cannot_be_compared() {
            assertThat(reject(BOOL + "DECLARE o Order\no = LOAD_ORDER(1)\nb = o = o\n"
                    + "SHOW b\nSHOW o")).contains("cannot be compared");
        }

        @Test
        void unrelated_types_cannot_be_compared() {
            assertThat(reject(INT + STR + BOOL + "b = n < s\nSHOW b\nSHOW n\nSHOW s"))
                    .contains("cannot compare");
        }
    }

    @Nested
    @DisplayName("conditions, loops and arrays")
    class Structure {

        @Test
        void a_condition_must_be_boolean() {
            assertThat(reject(INT + "IF n THEN\n    SHOW n\nEND IF"))
                    .contains("IF needs a BOOLEAN condition, but this one is INTEGER");
            assertThat(reject(INT + "DO WHILE n\n    SHOW n\nEND DO"))
                    .contains("DO needs a BOOLEAN condition");
        }

        @Test
        void logical_operators_need_booleans() {
            assertThat(reject(INT + BOOL + "b = b AND n\nSHOW b\nSHOW n"))
                    .contains("AND needs BOOLEAN on both sides");
        }

        @Test
        void a_FOR_loop_counts_in_integers() {
            assertThat(reject("DECLARE i INTEGER\nDECLARE d DECIMAL\nd = 1.0\n"
                    + "FOR i = 0 TO d\n    SHOW d\nEND FOR\nSHOW i"))
                    .contains("must be INTEGER, not DECIMAL");
        }

        @Test
        void an_index_must_be_an_integer_and_the_base_an_array() {
            assertThat(reject("DECLARE a[3] INTEGER\nDECLARE s STRING\ns = \"x\"\n"
                    + "a[s] = 1\nSHOW s\nSHOW a")).contains("array index must be INTEGER");
            assertThat(reject(INT + "n[1] = 2\nSHOW n"))
                    .contains("'n' is INTEGER and cannot be indexed");
        }

        @Test
        void an_element_takes_the_arrays_element_type() {
            assertThat(reject("DECLARE a[3] INTEGER\na[0] = 1\nSHOW a")).isNull();
            assertThat(reject("DECLARE a[3] INTEGER\nDECLARE s STRING\ns = \"x\"\n"
                    + "a[0] = s\nSHOW a\nSHOW s"))
                    .contains("must be INTEGER, but this one is STRING");
        }
    }

    @Nested
    @DisplayName("calls and constraints")
    class Calls {

        @Test
        void an_argument_must_be_assignable_to_its_parameter() {
            assertThat(reject(STR + "DECLARE o Order\no = LOAD_ORDER(s)\nSHOW o\nSHOW s"))
                    .contains("LOAD_ORDER takes INTEGER for 'orderId', but was given STRING");
        }

        @Test
        void an_integer_argument_widens_for_a_decimal_parameter() {
            assertThat(reject(DEC + "DECLARE o Order\no = LOAD_ORDER(1)\nd = ORDER_TOTAL(o)\n"
                    + "SHOW d\nSHOW o")).isNull();
        }

        @Test
        void a_NUMBER_constraint_accepts_either_numeric_type_and_nothing_else() {
            assertThat(reject(INT + "SCALE BY 2\nSHOW n")).isNull();
            assertThat(reject(INT + "SCALE BY 2.5\nSHOW n")).isNull();
            assertThat(reject(STR + "SCALE BY s\nSHOW s"))
                    .contains("'by' must be a number, but this one is STRING");
        }

        @Test
        void a_return_value_must_match_the_declared_type() {
            final var thrown = catchThrowableOfType(BubasException.class, () -> {
                final var source = "PROGRAM P RETURNS BOOLEAN\nRETURN 1\nEND.";
                final var program = StatementParser.parse(Lexer.lex(source), LANGUAGE);
                TypeChecker.check(program, LANGUAGE, FlowAnalyser.check(program, LANGUAGE));
            });
            assertThat(thrown.getMessage())
                    .contains("declares RETURNS BOOLEAN, so it cannot return INTEGER");
        }
    }
}
