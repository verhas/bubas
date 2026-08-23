package javax0.bubas.analyser;

import javax0.bubas.analyser.symbol.Variable;
import javax0.bubas.api.BubasException;
import javax0.bubas.api.BubasType;
import javax0.bubas.api.Context;
import javax0.bubas.api.ExpressionArg;
import javax0.bubas.api.StatementContext;
import javax0.bubas.api.VariableArg;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/** The whole front end, end to end: source in, checked program out. */
class CompileTest {

    public static final class Order {
    }

    public static final class LoadOrder {
        public Order call(Context ctx, long orderId) {
            return new Order();
        }
    }

    public static final class OrderWasFound {
        public boolean call(Context ctx, Order order) {
            return order != null;
        }
    }

    public static final class OrderTotal {
        public BigDecimal call(Context ctx, Order order) {
            return BigDecimal.ONE;
        }
    }

    public static final class LogEvent {
        public void call(Context ctx, String level, String message) {
        }
    }

    public static final class Declare {
        public void call(StatementContext ctx, VariableArg name, BubasType type) {
        }
    }

    public static final class DeclareFinal {
        public void call(StatementContext ctx, VariableArg name, BubasType type, ExpressionArg init) {
        }
    }

    public static final class Assign {
        public void call(StatementContext ctx, VariableArg name, ExpressionArg value) {
        }
    }

    private static final BubasLanguage LANGUAGE = BubasLanguage.builder()
            .defineOpaqueType("Order", Order.class)
            .defineFunction("LOAD_ORDER", LoadOrder.class)
            .defineFunction("ORDER_WAS_FOUND", OrderWasFound.class)
            .defineFunction("ORDER_TOTAL", OrderTotal.class)
            .defineFunction("LOG_EVENT", LogEvent.class)
            .defineStatement("DECLARE {new > identifier/T:name > declared} {type:T}", Declare.class)
            .defineStatement("DECLARE {new > identifier/T:name > final} {type:T} FINAL "
                    + "= {expression/T:init}", DeclareFinal.class)
            .defineStatement("{mutable:declared > var:name > initialized} = {expression/name:value}",
                    Assign.class)
            .seal();

    /**
     * The example from {@code README.md}, verbatim. Kept in sync by hand, deliberately: if the
     * documentation's flagship program stops compiling, this test is the thing that says so.
     */
    private static final String APPROVE_ORDER = """
            PROGRAM ApproveOrder(orderId INTEGER, limit DECIMAL) RETURNS BOOLEAN
                DECLARE purchase Order
                DECLARE total DECIMAL
                DECLARE taxRate DECIMAL FINAL = 0.07
            
                purchase = LOAD_ORDER(orderId)
            
                IF NOT ORDER_WAS_FOUND(purchase) THEN
                    LOG_EVENT "ERROR", "no such order: " + orderId
                    RETURN FALSE
                END IF
            
                total = ORDER_TOTAL(purchase) * (1.0 + taxRate)
            
                IF total > limit THEN
                    LOG_EVENT "INFO", "over limit: " + total
                    RETURN FALSE
                END IF
            
                RETURN TRUE
            END.
            """;

    private static String rejection(String source) {
        return catchThrowableOfType(BubasException.class, () -> LANGUAGE.compile(source))
                .getMessage();
    }

    @Nested
    @DisplayName("a realistic program")
    class Realistic {

        @Test
        void the_readme_example_compiles() {
            final var program = LANGUAGE.compile(APPROVE_ORDER);
            assertThat(program.name()).isEqualTo("ApproveOrder");
            assertThat(program.returns()).isEqualTo(BubasType.BOOLEAN);
            assertThat(program.parameters()).extracting(p -> p.name().text())
                    .containsExactly("orderId", "limit");
        }

        @Test
        void every_variable_is_recorded_with_parameters_first() {
            assertThat(LANGUAGE.compile(APPROVE_ORDER).variables())
                    .extracting(Variable::name)
                    .containsExactly("orderId", "limit", "purchase", "total", "taxRate");
        }

        @Test
        void a_parameter_and_a_FINAL_declaration_are_both_immutable() {
            assertThat(LANGUAGE.compile(APPROVE_ORDER).variables())
                    .filteredOn(Variable::isFinal)
                    .extracting(Variable::name)
                    .containsExactly("orderId", "limit", "taxRate");
        }

        @Test
        void the_program_is_reusable_and_carries_its_language() {
            final var program = LANGUAGE.compile(APPROVE_ORDER);
            assertThat(program.language()).isSameAs(LANGUAGE);
            assertThat(program.body()).isNotEmpty();
            assertThat(LANGUAGE.compile(APPROVE_ORDER).name()).isEqualTo(program.name());
        }
    }

    @Nested
    @DisplayName("every stage can still reject")
    class Stages {

        @Test
        void the_lexer_rejects() {
            assertThat(rejection("PROGRAM P\n    x = \"unterminated\nEND."))
                    .isEqualTo("unterminated string literal");
        }

        @Test
        void the_parser_rejects() {
            assertThat(rejection("PROGRAM P\n    IF TRUE THEN\nEND."))
                    .contains("expected END");
        }

        @Test
        void the_matcher_rejects() {
            assertThat(rejection("PROGRAM P\n    FROBNICATE x\nEND."))
                    .isEqualTo("unknown statement FROBNICATE");
        }

        @Test
        void the_flow_analyser_rejects() {
            assertThat(rejection("""
                    PROGRAM P
                        DECLARE x INTEGER
                        DECLARE y INTEGER
                        y = x
                        LOG_EVENT "INFO", "" + y
                    END.""")).contains("'x' is read before it is assigned");
        }

        @Test
        void the_type_checker_rejects() {
            assertThat(rejection("""
                    PROGRAM P
                        DECLARE x INTEGER
                        x = "text"
                        LOG_EVENT "INFO", "" + x
                    END.""")).contains("must be INTEGER, but this one is STRING");
        }

        @Test
        void a_diagnostic_carries_the_line_and_its_source() {
            final var thrown = catchThrowableOfType(BubasException.class, () -> LANGUAGE.compile("""
                    PROGRAM P
                        DECLARE x INTEGER
                        x = "text"
                        LOG_EVENT "INFO", "" + x
                    END."""));
            assertThat(thrown.getLine()).isEqualTo(3);
            assertThat(thrown.getSourceLine()).isEqualTo("    x = \"text\"");
            assertThat(thrown.getDiagnostic()).startsWith("line 3:");
        }
    }
}
