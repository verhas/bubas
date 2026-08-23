package javax0.bubas.analyser.flow;

import javax0.bubas.analyser.BubasLanguage;
import javax0.bubas.analyser.statement.StatementParser;
import javax0.bubas.api.BubasException;
import javax0.bubas.api.Context;
import javax0.bubas.api.ExpressionArg;
import javax0.bubas.api.BubasType;
import javax0.bubas.api.StatementContext;
import javax0.bubas.api.VariableArg;
import javax0.bubas.lexer.Lexer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class FlowAnalyserTest {

    public static final class Order {
    }

    public static final class LogEvent {
        public void call(Context ctx, String message) {
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

    public static final class Show {
        public void call(StatementContext ctx, VariableArg value) {
        }
    }

    private static final BubasLanguage LANGUAGE = BubasLanguage.builder()
            .defineOpaqueType("Order", Order.class)
            .defineFunction("LOG_EVENT", LogEvent.class)
            .defineStatement("DECLARE {new > identifier/T:name > declared} {type:T}", Declare.class)
            .defineStatement("DECLARE {new > identifier/T:name > final} {type:T} FINAL "
                    + "= {expression/T:init}", DeclareFinal.class)
            .defineStatement("{mutable:declared > var:name > initialized} = {expression/name:value}",
                    Assign.class)
            .defineStatement("SHOW {initialized > var:value}", Show.class)
            .seal();

    private static void check(String body) {
        final var source = "PROGRAM P\n" + body + "\nEND.";
        FlowAnalyser.check(StatementParser.parse(Lexer.lex(source), LANGUAGE), LANGUAGE);
    }

    private static void checkWhole(String source) {
        FlowAnalyser.check(StatementParser.parse(Lexer.lex(source), LANGUAGE), LANGUAGE);
    }

    /** The rejection message, or null when the body is accepted. */
    private static String reject(String body) {
        final var thrown = catchThrowableOfType(BubasException.class, () -> check(body));
        return thrown == null ? null : thrown.getMessage();
    }

    @Nested
    @DisplayName("definite assignment")
    class Definite {

        @Test
        void reading_before_assigning_is_rejected() {
            assertThat(reject("DECLARE x INTEGER\nSHOW x"))
                    .contains("'x' is not definitely assigned here");
        }

        @Test
        void assigning_then_reading_is_fine() {
            assertThat(reject("DECLARE x INTEGER\nx = 1\nSHOW x")).isNull();
        }

        @Test
        void an_IF_without_ELSE_guarantees_nothing() {
            assertThat(reject("DECLARE x INTEGER\nIF TRUE THEN\n    x = 1\nEND IF\nSHOW x"))
                    .contains("not definitely assigned");
        }

        @Test
        void an_IF_with_ELSE_assigning_in_both_arms_guarantees_it() {
            assertThat(reject("""
                    DECLARE x INTEGER
                    IF TRUE THEN
                        x = 1
                    ELSE
                        x = 2
                    END IF
                    SHOW x""")).isNull();
        }

        @Test
        void one_arm_forgetting_loses_it_for_the_whole_chain() {
            assertThat(reject("""
                    DECLARE x INTEGER
                    IF TRUE THEN
                        x = 1
                    ELSEIF FALSE THEN
                        x = 2
                    ELSE
                        LOG_EVENT "nothing"
                    END IF
                    SHOW x""")).contains("not definitely assigned");
        }

        @Test
        void an_arm_that_returns_contributes_nothing_rather_than_ruling_everything_out() {
            assertThat(reject("""
                    DECLARE x INTEGER
                    IF TRUE THEN
                        RETURN
                    ELSE
                        x = 2
                    END IF
                    SHOW x""")).isNull();
        }

        @Test
        void a_pre_test_loop_guarantees_nothing_because_the_body_may_not_run() {
            assertThat(reject("""
                    DECLARE x INTEGER
                    DO WHILE TRUE
                        x = 1
                    END DO
                    SHOW x""")).contains("not definitely assigned");
        }

        @Test
        void a_post_test_loop_keeps_what_its_body_assigned() {
            assertThat(reject("""
                    DECLARE x INTEGER
                    DO
                        x = 1
                    END DO UNTIL TRUE
                    SHOW x""")).isNull();
        }

        @Test
        void an_expression_may_not_read_an_unassigned_variable() {
            assertThat(reject("DECLARE x INTEGER\nDECLARE y INTEGER\ny = x + 1\nSHOW y"))
                    .contains("'x' is read before it is assigned");
        }
    }

    @Nested
    @DisplayName("loops")
    class Loops {

        @Test
        void a_FOR_variable_is_assigned_on_entry_and_survives_the_loop() {
            assertThat(reject("DECLARE i INTEGER\nFOR i = 0 TO 4\n    LOG_EVENT \"x\"\nEND FOR\nSHOW i"))
                    .isNull();
        }

        @Test
        void a_FOR_variable_must_be_an_INTEGER_that_is_not_final() {
            assertThat(reject("DECLARE i STRING\nFOR i = 0 TO 4\n    LOG_EVENT \"x\"\nEND FOR\nSHOW i"))
                    .contains("a FOR variable counts, so it must be INTEGER");
            assertThat(reject("DECLARE i INTEGER FINAL = 0\nFOR i = 0 TO 4\n"
                    + "    LOG_EVENT \"x\"\nEND FOR\nSHOW i"))
                    .contains("is final and cannot be a FOR variable");
        }

        @Test
        void the_body_may_not_assign_the_loop_variable() {
            assertThat(reject("DECLARE i INTEGER\nFOR i = 0 TO 4\n    i = 9\nEND FOR\nSHOW i"))
                    .contains("is the variable of an enclosing FOR loop");
        }

        @Test
        void EXIT_needs_an_enclosing_loop_of_its_kind() {
            assertThat(reject("DECLARE i INTEGER\nFOR i = 0 TO 4\n    EXIT DO\nEND FOR\nSHOW i"))
                    .contains("EXIT DO has no enclosing DO loop to leave");
            assertThat(reject("DECLARE i INTEGER\nFOR i = 0 TO 4\n    EXIT FOR\nEND FOR\nSHOW i"))
                    .isNull();
        }

        @Test
        void EXIT_FOR_reaches_past_an_inner_DO() {
            assertThat(reject("""
                    DECLARE i INTEGER
                    FOR i = 0 TO 4
                        DO WHILE TRUE
                            EXIT FOR
                        END DO
                    END FOR
                    SHOW i""")).isNull();
        }
    }

    @Nested
    @DisplayName("reachability and returns")
    class Reachability {

        @Test
        void a_statement_after_RETURN_cannot_be_reached() {
            assertThat(reject("RETURN\nLOG_EVENT \"never\""))
                    .contains("this statement cannot be reached");
        }

        @Test
        void a_statement_after_EXIT_cannot_be_reached() {
            assertThat(reject("""
                    DECLARE i INTEGER
                    FOR i = 0 TO 4
                        EXIT FOR
                        LOG_EVENT "never"
                    END FOR
                    SHOW i""")).contains("cannot be reached");
        }

        @Test
        void a_program_declaring_RETURNS_must_return_on_every_path() {
            final var thrown = catchThrowableOfType(BubasException.class,
                    () -> checkWhole("PROGRAM P RETURNS BOOLEAN\nIF TRUE THEN\n"
                            + "    RETURN TRUE\nEND IF\nEND."));
            assertThat(thrown.getMessage()).contains("can reach its end without returning a value");
        }

        @Test
        void returning_on_every_path_satisfies_it() {
            final var thrown = catchThrowableOfType(BubasException.class,
                    () -> checkWhole("PROGRAM P RETURNS BOOLEAN\nIF TRUE THEN\n"
                            + "    RETURN TRUE\nELSE\n    RETURN FALSE\nEND IF\nEND."));
            assertThat(thrown).isNull();
        }

        @Test
        void RETURN_and_the_header_must_agree_about_a_value() {
            assertThat(reject("RETURN TRUE")).contains("declares no RETURNS, so RETURN takes no value");
            final var thrown = catchThrowableOfType(BubasException.class,
                    () -> checkWhole("PROGRAM P RETURNS BOOLEAN\nRETURN\nEND."));
            assertThat(thrown.getMessage()).contains("so RETURN needs a value");
        }
    }

    @Nested
    @DisplayName("declarations and finality")
    class Declarations {

        @Test
        void a_final_variable_cannot_be_reassigned() {
            assertThat(reject("DECLARE rate DECIMAL FINAL = 1\nrate = 2\nSHOW rate"))
                    .contains("'rate' is final and cannot be changed");
        }

        @Test
        void a_declared_variable_nobody_reads_is_rejected() {
            assertThat(reject("DECLARE spare INTEGER")).contains("declared but never read");
        }

        @Test
        void assigning_is_not_reading() {
            assertThat(reject("DECLARE spare INTEGER\nspare = 1"))
                    .contains("declared but never read");
        }

        @Test
        void a_program_parameter_is_final_and_already_assigned() {
            final var thrown = catchThrowableOfType(BubasException.class,
                    () -> checkWhole("PROGRAM P(orderId INTEGER)\nSHOW orderId\nEND."));
            assertThat(thrown).isNull();
            final var reassigned = catchThrowableOfType(BubasException.class,
                    () -> checkWhole("PROGRAM P(orderId INTEGER)\norderId = 1\nSHOW orderId\nEND."));
            assertThat(reassigned.getMessage()).contains("is final and cannot be changed");
        }

        @Test
        void a_declaration_initializer_is_checked_like_any_other_expression() {
            // The declaring placeholder is handled separately from the rest, so the initializer's
            // reads could easily have been skipped; they are not.
            assertThat(reject("DECLARE y INTEGER\nDECLARE x INTEGER FINAL = y\nSHOW x"))
                    .contains("'y' is read before it is assigned");
            assertThat(reject("DECLARE y INTEGER\ny = 1\nDECLARE x INTEGER FINAL = y\nSHOW x"))
                    .isNull();
        }

        @Test
        void an_undeclared_name_is_reported() {
            assertThat(reject("ghost = 1")).contains("'ghost' is not declared");
        }
    }
}
