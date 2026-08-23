package javax0.bubas.analyser.match;

import javax0.bubas.analyser.pattern.PatternParser;
import javax0.bubas.api.BubasException;
import javax0.bubas.analyser.pattern.StatementPattern;
import javax0.bubas.lexer.Lexer;
import javax0.bubas.lexer.LogicalLine;
import javax0.bubas.lexer.Token;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class PatternMatcherTest {

    private static final List<StatementPattern> BUILT_INS = List.of(
            PatternParser.parse("DECLARE {new > identifier/T:name > declared} {type:T}"),
            PatternParser.parse("DECLARE {new > identifier/T:name > initialized} {type:T} = {expression/T:init}"),
            PatternParser.parse("DECLARE {new > identifier/ARRAY/T:name > initialized}"
                    + "[{expression/INTEGER:size}] {type:T}"),
            PatternParser.parse("{mutable:declared > var:name > initialized} = {expression/name:value}"));

    private static final Vocabulary VOCABULARY = Vocabulary.builder()
            .function("LOAD_ORDER", "ORDER_TOTAL", "COUNT_ORDERS")
            .opaqueType("Order")
            .patterns(BUILT_INS)
            .patterns(List.of(
                    PatternParser.parse("VALIDATE {initialized > var:item} AGAINST {expression:rules}"),
                    PatternParser.parse("PAY {expression:amount} VIA {var:account}"),
                    PatternParser.parse("ADD {literal/NUMBER:amount} TO {mutable:initialized > var:total > initialized}"),
                    PatternParser.parse("SELECT {literal/INTEGER:n} FROM {var:a} AND {var:b}")))
            .build();

    private static LogicalLine line(String source) {
        final var lines = Lexer.lex(source).stream().filter(LogicalLine::hasTokens).toList();
        assertThat(lines).hasSize(1);
        return lines.getFirst();
    }

    private static Optional<Match> match(String pattern, String source) {
        return PatternMatcher.match(line(source), PatternParser.parse(pattern), VOCABULARY);
    }

    private static Match matched(String pattern, String source) {
        final var m = match(pattern, source);
        assertThat(m).as("%s should match %s", source, pattern).isPresent();
        return m.get();
    }

    private static String text(List<Token> tokens) {
        return tokens.stream().map(Token::text).reduce("", (a, b) -> a.isEmpty() ? b : a + " " + b);
    }

    @Nested
    @DisplayName("literals and whole-line matching")
    class Literals {

        @Test
        void a_word_literal_matches_case_insensitively() {
            assertThat(match("VALIDATE {var:item} AGAINST {expression:rules}",
                    "validate shipment against rules")).isPresent();
        }

        @Test
        void a_pattern_must_consume_the_whole_line() {
            assertThat(match("PAY {expression:amount} VIA {var:account}",
                    "PAY 5 VIA acct EXTRA")).isEmpty();
        }

        @Test
        void a_line_too_short_does_not_match() {
            assertThat(match("PAY {expression:amount} VIA {var:account}", "PAY 5 VIA")).isEmpty();
        }

        @Test
        void a_different_keyword_does_not_match() {
            assertThat(match("PAY {expression:amount} VIA {var:account}", "SEND 5 VIA acct")).isEmpty();
        }
    }

    @Nested
    @DisplayName("names and references")
    class Names {

        @Test
        void an_identifier_binds_a_plain_name() {
            final var m = matched("DECLARE {new > identifier/T:name > declared} {type:T}",
                    "DECLARE counter INTEGER");
            assertThat(((Binding.Name) m.binding("name")).token().text()).isEqualTo("counter");
            assertThat(((Binding.TypeName) m.binding("T")).token().text()).isEqualTo("INTEGER");
        }

        @Test
        void an_identifier_refuses_a_reserved_word() {
            assertThat(match("DECLARE {new > identifier/T:name > declared} {type:T}",
                    "DECLARE VALIDATE INTEGER")).isEmpty();
        }

        @Test
        void a_type_placeholder_accepts_a_registered_opaque_type() {
            final var m = matched("DECLARE {new > identifier/T:name > declared} {type:T}",
                    "DECLARE shipment Order");
            assertThat(((Binding.TypeName) m.binding("T")).token().text()).isEqualTo("Order");
        }

        @Test
        void a_var_binds_a_bare_name_with_no_index() {
            final var r = (Binding.Reference) matched("SHOW {var:x}", "SHOW total").binding("x");
            assertThat(r.name().text()).isEqualTo("total");
            assertThat(r.index()).isEmpty();
        }

        @Test
        void a_var_absorbs_an_index_and_keeps_it_unevaluated() {
            final var r = (Binding.Reference) matched("SHOW {var:x}", "SHOW totals[i + 1]").binding("x");
            assertThat(r.name().text()).isEqualTo("totals");
            assertThat(text(r.index())).isEqualTo("i + 1");
        }

        @Test
        void an_index_may_contain_a_call() {
            final var r = (Binding.Reference) matched("SHOW {var:x}",
                    "SHOW items[COUNT_ORDERS() - 1]").binding("x");
            assertThat(text(r.index())).isEqualTo("COUNT_ORDERS ( ) - 1");
        }

        @Test
        void an_empty_index_does_not_match() {
            assertThat(match("SHOW {var:x}", "SHOW totals[]")).isEmpty();
        }
    }

    @Nested
    @DisplayName("expressions")
    class Expressions {

        @Test
        void an_expression_stops_at_a_reserved_word() {
            final var m = matched("PAY {expression:amount} VIA {var:account}",
                    "PAY base + tax VIA acct");
            assertThat(text(((Binding.Expression) m.binding("amount")).tokens()))
                    .isEqualTo("base + tax");
        }

        @Test
        void an_expression_does_not_stop_at_a_function_name() {
            final var m = matched("PAY {expression:amount} VIA {var:account}",
                    "PAY ORDER_TOTAL(o) * 2 VIA acct");
            assertThat(text(((Binding.Expression) m.binding("amount")).tokens()))
                    .isEqualTo("ORDER_TOTAL ( o ) * 2");
        }

        @Test
        void an_expression_runs_to_the_end_of_the_line_when_it_is_last() {
            final var m = matched("{mutable:declared > var:name > initialized} = {expression/name:value}",
                    "total = base + tax * 2");
            assertThat(text(((Binding.Expression) m.binding("value")).tokens()))
                    .isEqualTo("base + tax * 2");
        }

        @Test
        void a_comma_inside_a_call_does_not_end_the_expression() {
            final var m = matched("PAY {expression:amount} VIA {var:account}",
                    "PAY LOAD_ORDER(1, 2) VIA acct");
            assertThat(text(((Binding.Expression) m.binding("amount")).tokens()))
                    .isEqualTo("LOAD_ORDER ( 1 , 2 )");
        }

        @Test
        void an_expression_may_not_be_empty() {
            assertThat(match("PAY {expression:amount} VIA {var:account}", "PAY VIA acct")).isEmpty();
        }
    }

    @Nested
    @DisplayName("literal placeholders")
    class Constants {

        @Test
        void a_number_may_carry_a_sign() {
            final var c = (Binding.Constant) matched(
                    "ADD {literal/NUMBER:amount} TO {var:total}", "ADD -50.50 TO total")
                    .binding("amount");
            assertThat(c.sign().text()).isEqualTo("-");
            assertThat(c.token().asDecimal()).isEqualByComparingTo("50.50");
        }

        @Test
        void a_plus_is_accepted_too() {
            assertThat(match("ADD {literal/NUMBER:amount} TO {var:total}", "ADD +7 TO total"))
                    .isPresent();
        }

        @Test
        void an_unsigned_number_binds_no_sign() {
            final var c = (Binding.Constant) matched(
                    "ADD {literal/NUMBER:amount} TO {var:total}", "ADD 7 TO total").binding("amount");
            assertThat(c.sign()).isNull();
        }

        @Test
        void an_expression_is_not_a_constant() {
            assertThat(match("ADD {literal/NUMBER:amount} TO {var:total}", "ADD 3 - 5 TO total"))
                    .isEmpty();
        }

        @Test
        void a_constraint_narrows_which_constants_are_accepted() {
            assertThat(match("RETRY {literal/INTEGER:n}", "RETRY 3")).isPresent();
            assertThat(match("RETRY {literal/INTEGER:n}", "RETRY 3.5")).isEmpty();
            assertThat(match("RETRY {literal/INTEGER:n}", "RETRY \"three\"")).isEmpty();
            assertThat(match("TAG {literal/STRING:s}", "TAG \"urgent\"")).isPresent();
            assertThat(match("FLAG {literal/BOOLEAN:b}", "FLAG TRUE")).isPresent();
            assertThat(match("FLAG {literal/BOOLEAN:b}", "FLAG -TRUE")).isEmpty();
        }
    }

    @Nested
    @DisplayName("choosing among the registered patterns")
    class Choosing {

        private static final List<StatementPattern> REGISTERED = List.of(
                PatternParser.parse("PAY {expression:amount} VIA {var:account}"),
                PatternParser.parse("PAY {expression:amount} FROM {var:account}"),
                PatternParser.parse("VALIDATE {initialized > var:item} AGAINST {expression:rules}"),
                PatternParser.parse("{mutable:declared > var:name > initialized} = {expression/name:value}"));

        private static Match choose(String source) {
            return PatternMatcher.match(line(source), REGISTERED, VOCABULARY);
        }

        private static String rejection(String source) {
            return catchThrowableOfType(BubasException.class, () -> choose(source)).getMessage();
        }

        @Test
        void picks_the_one_pattern_that_matches() {
            assertThat(choose("PAY 5 VIA acct").pattern().source())
                    .isEqualTo("PAY {expression:amount} VIA {var:account}");
            assertThat(choose("PAY 5 FROM acct").pattern().source())
                    .isEqualTo("PAY {expression:amount} FROM {var:account}");
        }

        @Test
        void a_keywordless_pattern_is_reachable_too() {
            assertThat(choose("count = 0").pattern().keyword()).isEmpty();
        }

        @Test
        void an_unrecognised_first_word_is_an_unknown_statement() {
            assertThat(rejection("FOO 1 2")).isEqualTo("unknown statement FOO");
        }

        @Test
        void a_known_keyword_with_the_wrong_shape_names_the_pattern() {
            assertThat(rejection("VALIDATE order"))
                    .isEqualTo("VALIDATE does not match its pattern: "
                            + "VALIDATE {initialized > var:item} AGAINST {expression:rules}");
        }

        @Test
        void a_keyword_with_several_patterns_lists_them_all() {
            assertThat(rejection("PAY 5 TOWARDS acct"))
                    .startsWith("PAY does not match any of its patterns:")
                    .contains("PAY {expression:amount} VIA {var:account}")
                    .contains("PAY {expression:amount} FROM {var:account}");
        }

        @Test
        void two_patterns_matching_one_line_is_an_error_not_a_precedence_question() {
            final var ambiguous = List.of(
                    PatternParser.parse("SET {var:target} TO {expression:value}"),
                    PatternParser.parse("SET {var:target} TO {var:source}"));
            final var e = catchThrowableOfType(BubasException.class,
                    () -> PatternMatcher.match(line("SET a TO b"), ambiguous, VOCABULARY));
            assertThat(e.getMessage())
                    .startsWith("this line matches more than one pattern:")
                    .contains("SET {var:target} TO {expression:value}")
                    .contains("SET {var:target} TO {var:source}");
        }

        @Test
        void a_diagnostic_carries_the_line_and_its_source() {
            final var e = catchThrowableOfType(BubasException.class, () -> choose("FOO 1 2"));
            assertThat(e.getLine()).isEqualTo(1);
            assertThat(e.getSourceLine()).isEqualTo("FOO 1 2");
        }
    }

    @Nested
    @DisplayName("the built-in patterns")
    class BuiltIns {

        @Test
        void assignment_covers_a_plain_variable() {
            final var m = matched("{mutable:declared > var:name > initialized} = {expression/name:value}",
                    "count = 0");
            assertThat(((Binding.Reference) m.binding("name")).index()).isEmpty();
        }

        @Test
        void the_same_pattern_covers_an_array_element() {
            final var m = matched("{mutable:declared > var:name > initialized} = {expression/name:value}",
                    "numbers[i] = numbers[i] + 1");
            final var target = (Binding.Reference) m.binding("name");
            assertThat(target.name().text()).isEqualTo("numbers");
            assertThat(text(target.index())).isEqualTo("i");
            assertThat(text(((Binding.Expression) m.binding("value")).tokens()))
                    .isEqualTo("numbers [ i ] + 1");
        }

        @Test
        void an_array_declaration_matches() {
            final var m = matched("DECLARE {new > identifier/ARRAY/T:name > initialized}"
                            + "[{expression/INTEGER:size}] {type:T}",
                    "DECLARE items[COUNT_ORDERS()] Order");
            assertThat(((Binding.Name) m.binding("name")).token().text()).isEqualTo("items");
            assertThat(text(((Binding.Expression) m.binding("size")).tokens()))
                    .isEqualTo("COUNT_ORDERS ( )");
        }

        @Test
        void a_declaration_with_an_initializer_matches() {
            assertThat(match("DECLARE {new > identifier/T:name > initialized} {type:T} "
                    + "= {expression/T:init}", "DECLARE total DECIMAL = 0.0")).isPresent();
        }
    }
}
