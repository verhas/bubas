package javax0.bubas.lexer;

import javax0.bubas.api.BubasException;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class LexerTest {

    private static List<LogicalLine> lines(String source) {
        return Lexer.lex(source);
    }

    /** Only the lines that carry tokens; blank and comment-only lines are what the parser skips. */
    private static List<LogicalLine> code(String source) {
        return lines(source).stream().filter(LogicalLine::hasTokens).toList();
    }

    private static List<Token> tokens(String source) {
        final var l = code(source);
        assertThat(l).as("expected exactly one line with tokens").hasSize(1);
        return l.getFirst().tokens();
    }

    private static BubasException failure(String source) {
        return catchThrowableOfType(BubasException.class, () -> Lexer.lex(source));
    }

    /** Concatenates everything the lexer produced, in order. Must reproduce the input exactly. */
    private static String reconstruct(String source) {
        final var sb = new StringBuilder();
        for (final var line : lines(source)) {
            line.trivia().forEach(t -> sb.append(t.text()));
            for (final var token : line.tokens()) {
                sb.append(token.text());
                token.trailing().forEach(t -> sb.append(t.text()));
            }
        }
        return sb.toString();
    }

    @Nested
    @DisplayName("tokens")
    class Tokens {

        @Test
        void splits_a_statement_into_tokens() {
            assertThat(tokens("LET total = subtotal + 42"))
                    .extracting(Token::text)
                    .containsExactly("LET", "total", "=", "subtotal", "+", "42");
        }

        @Test
        void does_not_classify_keywords() {
            // The reserved set is not known until seal; everything word-shaped is a WORD.
            assertThat(tokens("IF x THEN"))
                    .extracting(Token::type)
                    .containsExactly(TokenType.WORD, TokenType.WORD, TokenType.WORD);
        }

        @Test
        void records_physical_line_and_column() {
            final var t = tokens("  LET x = 1").getFirst();
            assertThat(t.line()).isEqualTo(1);
            assertThat(t.column()).isEqualTo(3);
        }

        @Test
        void recognises_two_character_operators() {
            assertThat(tokens("a <> b <= c >= d"))
                    .filteredOn(t -> t.type() == TokenType.OPERATOR)
                    .extracting(Token::text)
                    .containsExactly("<>", "<=", ">=");
        }

        @Test
        void lexes_the_program_terminator_with_its_dot() {
            assertThat(tokens("END."))
                    .extracting(Token::type, Token::text)
                    .containsExactly(Tuple.tuple(TokenType.WORD, "END"),
                            Tuple.tuple(TokenType.PUNCT, "."));
        }

        @Test
        void treats_a_leading_minus_as_an_operator_not_part_of_the_literal() {
            // Unary minus is the parser's business; -10 is two tokens, so a-10 lexes the same way.
            assertThat(tokens("LET x = -10"))
                    .extracting(Token::text)
                    .containsExactly("LET", "x", "=", "-", "10");
        }

        @Test
        void accepts_unicode_letters_in_names() {
            assertThat(tokens("LET Kündigung = 1")).extracting(Token::text).contains("Kündigung");
            assertThat(tokens("LET añoFiscal = 1")).extracting(Token::text).contains("añoFiscal");
        }
    }

    @Nested
    @DisplayName("literals")
    class Literals {

        @Test
        void parses_integer_values() {
            assertThat(tokens("42").getFirst().asLong()).isEqualTo(42L);
        }

        @Test
        void parses_decimal_values_preserving_scale() {
            assertThat(tokens("10.50").getFirst().asDecimal()).isEqualTo(new BigDecimal("10.50"));
        }

        @Test
        void keeps_the_raw_lexeme_and_the_unescaped_value_apart() {
            final var t = tokens("\"a\\nb\"").getFirst();
            assertThat(t.text()).isEqualTo("\"a\\nb\"");
            assertThat(t.asString()).isEqualTo("a\nb");
        }

        @Test
        void unescapes_every_supported_sequence() {
            assertThat(tokens("\"\\n\\t\\r\\\\\\\"\"").getFirst().asString()).isEqualTo("\n\t\r\\\"");
        }

        @Test
        void rejects_an_unterminated_string() {
            assertThat(failure("LET s = \"oops").getMessage()).isEqualTo("unterminated string literal");
        }

        @Test
        void rejects_an_unknown_escape() {
            assertThat(failure("LET s = \"a\\qb\"").getMessage())
                    .isEqualTo("unknown escape sequence '\\q'");
        }

        @Test
        void rejects_a_number_running_into_a_name() {
            assertThat(failure("LET x = 1e5").getMessage())
                    .isEqualTo("malformed number literal '1e'");
        }

        @Test
        void rejects_an_integer_that_does_not_fit() {
            assertThat(failure("LET x = 99999999999999999999").getMessage())
                    .contains("does not fit in INTEGER");
        }
    }

    @Nested
    @DisplayName("comments")
    class Comments {

        @Test
        void strips_an_end_of_line_comment() {
            assertThat(tokens("LET x = 1   ' set it up"))
                    .extracting(Token::text)
                    .containsExactly("LET", "x", "=", "1");
        }

        @Test
        void a_comment_only_line_is_a_line_with_no_tokens() {
            final var l = lines("' just a note\n' and another");
            assertThat(l).hasSize(2);
            assertThat(l).allMatch(x -> !x.hasTokens());
            assertThat(l.getFirst().trivia())
                    .extracting(Trivia::type)
                    .containsExactly(TriviaType.COMMENT, TriviaType.NEWLINE);
        }

        @Test
        void an_apostrophe_inside_a_string_is_not_a_comment() {
            assertThat(tokens("LET s = \"it's fine\" + x"))
                    .extracting(Token::text)
                    .containsExactly("LET", "s", "=", "\"it's fine\"", "+", "x");
        }
    }

    @Nested
    @DisplayName("continuation")
    class Continuation {

        @Test
        void an_open_paren_joins_the_next_line() {
            assertThat(tokens("LET x = COMPUTE(alpha,\n                beta)"))
                    .extracting(Token::text)
                    .containsExactly("LET", "x", "=", "COMPUTE", "(", "alpha", ",", "beta", ")");
        }

        @Test
        void an_open_square_bracket_joins_the_next_line() {
            assertThat(tokens("LET first = names[index +\n                  offset]"))
                    .extracting(Token::text)
                    .containsExactly("LET", "first", "=", "names", "[", "index", "+", "offset", "]");
        }

        @Test
        void a_trailing_binary_operator_joins_the_next_line() {
            assertThat(tokens("LET total = subtotal +\n            tax +\n            shipping"))
                    .extracting(Token::text)
                    .containsExactly("LET", "total", "=", "subtotal", "+", "tax", "+", "shipping");
        }

        @Test
        void a_trailing_word_operator_joins_the_next_line() {
            assertThat(tokens("IF ready AND\n   willing THEN"))
                    .extracting(Token::text)
                    .containsExactly("IF", "ready", "AND", "willing", "THEN");
        }

        @Test
        void a_trailing_underscore_joins_the_next_line_and_disappears() {
            assertThat(tokens("VALIDATE order _\n    AGAINST rules"))
                    .extracting(Token::text)
                    .containsExactly("VALIDATE", "order", "AGAINST", "rules");
        }

        @Test
        void an_underscore_inside_a_name_is_not_a_continuation() {
            assertThat(code("LET order_id = 1\nLET x = 2")).hasSize(2);
        }

        @Test
        void a_comment_on_an_intermediate_line_is_stripped_before_joining() {
            assertThat(tokens("LET x = COMPUTE(alpha,   ' first\n                beta)   ' second"))
                    .extracting(Token::text)
                    .containsExactly("LET", "x", "=", "COMPUTE", "(", "alpha", ",", "beta", ")");
        }

        @Test
        void a_joined_line_reports_its_starting_line_and_its_whole_source() {
            final var l = code("' header\nLET total = a +\n            b").getFirst();
            assertThat(l.line()).isEqualTo(2);
            assertThat(l.source()).isEqualTo("LET total = a +\n            b");
        }

        @Test
        void tokens_keep_the_physical_line_they_were_written_on() {
            assertThat(code("LET total = a +\n            b").getFirst().tokens().getLast().line()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("trivia")
    class TriviaOwnership {

        @Test
        void lexing_is_lossless() {
            final var sources = List.of(
                    "",
                    "\n",
                    "LET x = 1",
                    "LET x = 1\n",
                    "\n\n   \n",
                    "' note\n\nLET x = 1   ' why\n\n' trailing\n",
                    "LET total = a +\n            b   ' joined\n",
                    "VALIDATE order _\n    AGAINST rules\n",
                    "LET x = f(1,\n          2)\r\nEND.\r\n");
            for (final var source : sources) {
                assertThat(reconstruct(source)).as("round trip of %s", source).isEqualTo(source);
            }
        }

        @Test
        void whitespace_before_the_first_token_belongs_to_the_line() {
            final var l = code("    LET x = 1").getFirst();
            assertThat(l.trivia()).singleElement()
                    .extracting(Trivia::type, Trivia::text)
                    .containsExactly(TriviaType.WHITESPACE, "    ");
        }

        @Test
        void an_end_of_line_comment_trails_the_last_token() {
            final var l = code("LET x = 1  ' why\n").getFirst();
            assertThat(l.tokens().getLast().trailing())
                    .extracting(Trivia::type)
                    .containsExactly(TriviaType.WHITESPACE, TriviaType.COMMENT, TriviaType.NEWLINE);
        }

        @Test
        void the_terminator_belongs_to_the_line_it_ends() {
            final var l = code("LET x = 1\nLET y = 2\n");
            assertThat(l.getFirst().tokens().getLast().trailing())
                    .extracting(Trivia::type)
                    .containsExactly(TriviaType.NEWLINE);
            assertThat(l.get(1).trivia()).isEmpty();
        }

        @Test
        void the_continuation_underscore_becomes_trivia_of_the_token_before_it() {
            final var l = code("VALIDATE order _\n    AGAINST rules").getFirst();
            final var order = l.tokens().get(1);
            assertThat(order.text()).isEqualTo("order");
            assertThat(order.trailing())
                    .extracting(Trivia::type)
                    .containsExactly(TriviaType.WHITESPACE, TriviaType.CONTINUATION,
                            TriviaType.NEWLINE, TriviaType.WHITESPACE);
        }

        @Test
        void a_blank_line_owns_its_own_terminator() {
            final var l = lines("LET x = 1\n\nEND.");
            assertThat(l).hasSize(3);
            assertThat(l.get(1).hasTokens()).isFalse();
            assertThat(l.get(1).trivia())
                    .extracting(Trivia::type)
                    .containsExactly(TriviaType.NEWLINE);
        }

        @Test
        void trivia_carries_its_own_position() {
            final var l = code("LET x = 1  ' why").getFirst();
            final var comment = l.tokens().getLast().trailing().get(1);
            assertThat(comment.type()).isEqualTo(TriviaType.COMMENT);
            assertThat(comment.line()).isEqualTo(1);
            assertThat(comment.column()).isEqualTo(12);
        }
    }

    @Nested
    @DisplayName("structure and failures")
    class Structure {

        @Test
        void separates_logical_lines() {
            assertThat(code("DECLARE x INTEGER\nLET x = 1\n\nEND.")).hasSize(3);
            assertThat(lines("DECLARE x INTEGER\nLET x = 1\n\nEND.")).hasSize(4);
        }

        @Test
        void handles_windows_line_endings() {
            assertThat(code("DECLARE x INTEGER\r\nLET x = 1\r\n")).hasSize(2);
        }

        @Test
        void a_source_ending_in_a_terminator_has_no_extra_line_after_it() {
            assertThat(lines("LET x = 1\n")).hasSize(1);
            assertThat(lines("LET x = 1\n\n")).hasSize(2);
        }

        @Test
        void an_unclosed_bracket_names_the_line_it_opened_on() {
            final var e = failure("LET x = f(1,\n2\nDECLARE y INTEGER\nEND.");
            assertThat(e.getLine()).isEqualTo(1);
            assertThat(e.getMessage()).isEqualTo("'(' opened here is never closed");
        }

        @Test
        void reports_the_outermost_unclosed_bracket() {
            final var e = failure("LET x = f(g(1)");
            assertThat(e.getMessage()).startsWith("'(' opened here");
            assertThat(e.getSourceLine()).isEqualTo("LET x = f(g(1)");
        }

        @Test
        void rejects_an_unmatched_closing_bracket() {
            assertThat(failure("LET x = 1)").getMessage()).isEqualTo("unmatched ')'");
        }

        @Test
        void rejects_mismatched_bracket_kinds() {
            assertThat(failure("LET x = f(1]").getMessage())
                    .isEqualTo("']' does not match '(' opened on line 1");
        }

        @Test
        void rejects_a_source_that_ends_mid_line() {
            assertThat(failure("LET x = 1 +").getMessage())
                    .isEqualTo("source ends in the middle of a line");
        }

        @Test
        void rejects_an_unexpected_character() {
            assertThatThrownBy(() -> Lexer.lex("LET x = 1 # 2"))
                    .isInstanceOf(BubasException.class)
                    .hasMessage("unexpected character '#'");
        }

        @Test
        void empty_source_yields_no_lines() {
            assertThat(lines("")).isEmpty();
        }

        @Test
        void a_diagnostic_renders_position_and_source() {
            assertThat(failure("LET x = 1\nLET s = \"oops").getDiagnostic())
                    .isEqualTo("line 2: unterminated string literal\n    LET s = \"oops");
        }
    }
}
