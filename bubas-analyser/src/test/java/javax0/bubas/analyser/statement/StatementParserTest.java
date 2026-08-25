package javax0.bubas.analyser.statement;

import javax0.bubas.analyser.BubasLanguage;
import javax0.bubas.support.Standard;
import javax0.bubas.api.BubasException;
import javax0.bubas.api.BubasType;
import javax0.bubas.api.Context;
import javax0.bubas.api.ExpressionArg;
import javax0.bubas.api.StatementContext;
import javax0.bubas.api.VariableArg;
import javax0.bubas.lexer.Lexer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class StatementParserTest {

    public static final class Order {
    }

    public static final class LoadOrder {
        public Order call(Context ctx, long orderId) {
            return new Order();
        }
    }

    public static final class LogEvent {
        public void call(Context ctx, String level, String message) {
        }
    }

    public static final class CountOrders {
        public long call(Context ctx) {
            return 0;
        }
    }

    public static final class Validate {
        public void call(StatementContext ctx, VariableArg item, ExpressionArg rules) {
        }
    }

    public static final class AddTo {
        public void call(StatementContext ctx, long amount, VariableArg total) {
        }
    }

    /** A builder with the standard declaration and assignment statements already installed. */
    private static BubasLanguage.Builder standard() {
        final var builder = BubasLanguage.builder().install(Standard::register);
        return builder;
    }

    private static final BubasLanguage LANGUAGE = standard()
            .defineOpaqueType("Order", Order.class)
            .defineFunction("LOAD_ORDER", LoadOrder.class)
            .defineFunction("LOG_EVENT", LogEvent.class)
            .defineFunction("COUNT_ORDERS", CountOrders.class)
            .defineStatement("VALIDATE {initialized > var:item} AGAINST {expression:rules}",
                    Validate.class)
            .defineStatement("ADD {literal/INTEGER:amount} TO {mutable:initialized > var:total"
                    + " > initialized}", AddTo.class)
            .seal();

    private static Program parse(String source) {
        return StatementParser.parse(Lexer.lex(source), LANGUAGE);
    }

    private static Program program(String body) {
        return parse("PROGRAM P\n" + body + "\nEND.");
    }

    private static String rejection(String source) {
        return catchThrowableOfType(BubasException.class, () -> parse(source)).getMessage();
    }

    /** The rejection message, or null when the body parses cleanly. */
    private static String inProgram(String body) {
        final var thrown = catchThrowableOfType(BubasException.class, () -> program(body));
        return thrown == null ? null : thrown.getMessage();
    }

    @Nested
    @DisplayName("the program header")
    class Header {

        @Test
        void a_bare_program_parses() {
            final var p = parse("PROGRAM Simple\nEND.");
            assertThat(p.name().text()).isEqualTo("Simple");
            assertThat(p.parameters()).isEmpty();
            assertThat(p.returns()).isNull();
            assertThat(p.body()).isEmpty();
        }

        @Test
        void END_may_stand_without_its_dot() {
            assertThat(parse("PROGRAM Simple\nEND").name().text()).isEqualTo("Simple");
        }

        @Test
        void parameters_and_a_return_type_are_read() {
            final var p = parse("PROGRAM P(orderId INTEGER, region STRING) RETURNS BOOLEAN\nEND.");
            assertThat(p.parameters()).extracting(param -> param.name().text())
                    .containsExactly("orderId", "region");
            assertThat(p.parameters()).extracting(Program.Parameter::type)
                    .containsExactly(BubasType.INTEGER, BubasType.STRING);
            assertThat(p.returns()).isEqualTo(BubasType.BOOLEAN);
        }

        @Test
        void a_parameter_may_be_an_opaque_type() {
            final var p = parse("PROGRAM P(subject Order)\nEND.");
            assertThat(p.parameters().getFirst().type())
                    .isEqualTo(BubasType.opaque("Order", Order.class));
        }

        @Test
        void a_header_may_span_lines_by_continuation() {
            final var p = parse("PROGRAM P(orderId INTEGER,\n          region STRING) RETURNS BOOLEAN\nEND.");
            assertThat(p.parameters()).hasSize(2);
        }

        @Test
        void a_source_without_a_program_is_rejected() {
            assertThat(rejection("x = 1")).contains("a program starts with PROGRAM");
        }

        @Test
        void a_line_after_the_end_is_rejected() {
            assertThat(rejection("PROGRAM P\nEND.\nx = 1"))
                    .contains("a source contains one program");
        }

        @Test
        void an_empty_parameter_list_is_rejected() {
            assertThat(rejection("PROGRAM P()\nEND."))
                    .contains("omit the parentheses instead");
        }
    }

    @Nested
    @DisplayName("blocks")
    class Blocks {

        @Test
        void an_if_with_elseif_and_else_keeps_every_arm() {
            final var p = program("""
                    IF a THEN
                        x = 1
                    ELSEIF b THEN
                        x = 2
                    ELSE
                        x = 3
                    END IF""");
            final var branch = (Statement.If) p.body().getFirst();
            assertThat(branch.branches()).hasSize(2);
            assertThat(branch.otherwise()).hasSize(1);
        }

        @Test
        void an_if_without_else_has_none() {
            final var branch = (Statement.If) program("IF a THEN\n    x = 1\nEND IF")
                    .body().getFirst();
            assertThat(branch.otherwise()).isNull();
        }

        @Test
        void a_pre_test_loop_records_where_its_condition_sits() {
            final var loop = (Statement.Loop) program("DO WHILE a\n    x = 1\nEND DO")
                    .body().getFirst();
            assertThat(loop.testAtEnd()).isFalse();
            assertThat(loop.until()).isFalse();
        }

        @Test
        void a_post_test_loop_takes_its_condition_from_END_DO() {
            final var loop = (Statement.Loop) program("DO\n    x = 1\nEND DO UNTIL a")
                    .body().getFirst();
            assertThat(loop.testAtEnd()).isTrue();
            assertThat(loop.until()).isTrue();
        }

        @Test
        void a_loop_may_not_test_at_both_ends() {
            assertThat(inProgram("DO WHILE a\n    x = 1\nEND DO WHILE b"))
                    .contains("already tests its condition at DO");
        }

        @Test
        void a_loop_must_test_somewhere() {
            assertThat(inProgram("DO\n    x = 1\nEND DO"))
                    .contains("tests its condition nowhere");
        }

        @Test
        void a_for_loop_reads_its_bounds_and_optional_step() {
            final var loop = (Statement.For) program("FOR i = 0 TO 4\n    x = 1\nEND FOR")
                    .body().getFirst();
            assertThat(loop.variable().text()).isEqualTo("i");
            assertThat(loop.step()).isNull();
            final var stepped = (Statement.For) program("FOR i = 10 TO 0 STEP -2\n    x = 1\nEND FOR")
                    .body().getFirst();
            assertThat(stepped.step()).isNotNull();
        }

        @Test
        void exit_names_the_loop_it_leaves() {
            final var p = program("FOR i = 0 TO 4\n    EXIT FOR\nEND FOR");
            final var loop = (Statement.For) p.body().getFirst();
            assertThat(((Statement.Exit) loop.body().getFirst()).fromFor()).isTrue();
            assertThat(inProgram("FOR i = 0 TO 4\n    EXIT\nEND FOR"))
                    .contains("EXIT names the loop it leaves");
        }

        @Test
        void blocks_nest() {
            final var p = program("""
                    FOR i = 0 TO 4
                        IF a THEN
                            DO WHILE b
                                x = 1
                            END DO
                        END IF
                    END FOR""");
            final var outer = (Statement.For) p.body().getFirst();
            final var inner = (Statement.If) outer.body().getFirst();
            assertThat(inner.branches().getFirst().body().getFirst())
                    .isInstanceOf(Statement.Loop.class);
        }

        @Test
        void an_unclosed_block_is_reported_against_the_line_that_opened_it() {
            final var e = catchThrowableOfType(BubasException.class,
                    () -> parse("PROGRAM P\nIF a THEN\n    x = 1\nEND."));
            assertThat(e.getMessage()).contains("expected END");
            assertThat(e.getLine()).isEqualTo(4);
        }
    }

    @Nested
    @DisplayName("ordinary lines")
    class Lines {

        @Test
        void a_return_may_carry_a_value_or_not() {
            assertThat(((Statement.Return) program("RETURN").body().getFirst()).value()).isNull();
            assertThat(((Statement.Return) program("RETURN TRUE").body().getFirst()).value())
                    .isNotNull();
        }

        @Test
        void a_void_function_may_be_called_bare_or_with_parentheses() {
            final var bare = (Statement.Call) program("LOG_EVENT \"INFO\", msg").body().getFirst();
            final var parens = (Statement.Call) program("LOG_EVENT(\"INFO\", msg)").body().getFirst();
            assertThat(bare.arguments()).hasSize(2);
            assertThat(parens.arguments()).hasSize(2);
            assertThat(bare.signature().name()).isEqualTo("LOG_EVENT");
        }

        @Test
        void a_function_with_a_value_may_not_stand_alone() {
            assertThat(inProgram("LOAD_ORDER(42)"))
                    .contains("cannot stand alone as a statement")
                    .contains("discarded silently");
        }

        @Test
        void a_bare_call_checks_its_arity() {
            assertThat(inProgram("LOG_EVENT \"INFO\"")).contains("takes 2 argument(s)");
        }

        @Test
        void a_line_matching_a_pattern_becomes_a_command_with_parsed_arguments() {
            final var command = (Statement.Command) program("VALIDATE subject AGAINST rules + 1")
                    .body().getFirst();
            assertThat(command.definition().pattern().source()).startsWith("VALIDATE");
            assertThat(command.arguments().get("item")).isInstanceOf(Argument.Reference.class);
            assertThat(command.arguments().get("rules")).isInstanceOf(Argument.Expr.class);
        }

        @Test
        void a_declaration_resolves_its_type_argument() {
            final var command = (Statement.Command) program("DECLARE total DECIMAL")
                    .body().getFirst();
            assertThat(((Argument.TypeName) command.arguments().get("T")).type())
                    .isEqualTo(BubasType.DECIMAL);
            assertThat(command.arguments().get("name")).isInstanceOf(Argument.Name.class);
        }

        @Test
        void an_indexed_target_carries_its_index_as_an_expression() {
            final var command = (Statement.Command) program("numbers[i + 1] = 0").body().getFirst();
            final var target = (Argument.Reference) command.arguments().get("name");
            assertThat(target.token().text()).isEqualTo("numbers");
            assertThat(target.index()).isNotNull();
        }

        @Test
        void a_signed_constant_arrives_as_one_value() {
            final var command = (Statement.Command) program("ADD -5 TO total").body().getFirst();
            assertThat(((Argument.Constant) command.arguments().get("amount")).value())
                    .isEqualTo(-5L);
        }

        @Test
        void a_declaration_may_only_appear_at_the_top_level() {
            // BUBAS has no local variables, so a declaration inside a block would look scoped
            // while being global. The rule covers any pattern that creates a variable, not DECLARE
            // alone — and DECLARE is itself only a pattern.
            for (final var block : new String[]{
                    "IF a THEN\n    DECLARE x INTEGER\nEND IF",
                    "ELSE arm:IF a THEN\n    x = 1\nELSE\n    DECLARE x INTEGER\nEND IF",
                    "DO WHILE a\n    DECLARE x INTEGER\nEND DO",
                    "FOR i = 0 TO 4\n    DECLARE x INTEGER\nEND FOR"}) {
                final var source = block.contains("ELSE arm:")
                        ? block.substring(block.indexOf(':') + 1) : block;
                assertThat(inProgram(source))
                        .as("declaration inside %s", source)
                        .contains("may only be declared at the top level")
                        .contains("no local variables");
            }
        }

        @Test
        void a_declaration_at_the_top_level_is_fine_however_deep_the_program_goes() {
            assertThat(program("""
                    DECLARE total DECIMAL
                    FOR i = 0 TO 4
                        total = 1
                    END FOR
                    DECLARE spare INTEGER""").body()).hasSize(3);
        }

        @Test
        void a_non_declaring_command_is_welcome_inside_a_block() {
            assertThat(inProgram("IF a THEN\n    VALIDATE subject AGAINST rules\nEND IF"))
                    .isNull();
        }

        @Test
        void an_unmatched_line_is_reported_by_the_matcher() {
            assertThat(inProgram("VALIDATE subject"))
                    .contains("VALIDATE does not match its pattern");
        }
    }
}
