package javax0.bubas.lexer;

/**
 * One run of insignificant text.
 *
 * @param type   what kind of trivia this is
 * @param text   the text exactly as written, including the leading apostrophe of a comment
 * @param line   the 1-based physical line it starts on
 * @param column the 1-based column it starts at
 */
public record Trivia(TriviaType type, String text, int line, int column) {

    @Override
    public String toString() {
        return type + "(" + text.replace("\n", "\\n").replace("\r", "\\r") + ")@" + line + ":" + column;
    }
}
