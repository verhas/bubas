package javax0.bubas.lexer;

import java.math.BigDecimal;
import java.util.List;

/**
 * One token, together with the trivia that follows it.
 * <p>
 * Trivia has exactly one owner. Everything between two tokens belongs to the earlier one, and
 * everything before the first token of a line belongs to {@link LogicalLine#trivia()}. With
 * <em>n</em> tokens there are <em>n+1</em> gaps and <em>n+1</em> slots, so no run of text is ever
 * claimed twice and none is unclaimed. Concatenating a line's trivia, then each token's text and
 * trailing trivia in order, reproduces the source exactly.
 *
 * @param type     what kind of token this is
 * @param text     the lexeme exactly as written, quotes and all for a string literal
 * @param line     the 1-based <em>physical</em> line the token was written on; a logical line may
 *                 span several, and a diagnostic about a token should point at the token
 * @param column   the 1-based column of the token's first character on that physical line
 * @param value    the parsed value for {@link TokenType#INTEGER} ({@link Long}),
 *                 {@link TokenType#DECIMAL} ({@link BigDecimal}) and {@link TokenType#STRING}
 *                 (unescaped {@link String}); {@code null} otherwise
 * @param trailing the trivia between this token and the next, including the line terminator when
 *                 this is the last token of a line
 */
public record Token(TokenType type, String text, int line, int column, Object value,
                    List<Trivia> trailing) {

    public long asLong() {
        return (Long) value;
    }

    public BigDecimal asDecimal() {
        return (BigDecimal) value;
    }

    public String asString() {
        return (String) value;
    }

    @Override
    public String toString() {
        return type + "(" + text + ")@" + line + ":" + column;
    }
}
