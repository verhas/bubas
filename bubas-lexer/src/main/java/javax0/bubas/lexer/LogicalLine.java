package javax0.bubas.lexer;

import java.util.List;

/**
 * A logical line: the unit a statement pattern matches against. Physical lines joined by
 * continuation are already merged.
 * <p>
 * A blank or comment-only physical line is a logical line with <em>zero</em> tokens that owns all
 * of its trivia. That is why nothing needs a file-level trivia slot: trailing blank lines and a
 * final comment block are simply more zero-token lines. The parser skips them.
 *
 * @param line   the 1-based physical line on which this logical line starts
 * @param source the physical lines it was assembled from, joined by their own terminators and
 *               without the final one, exactly as written — this is what a diagnostic shows
 * @param trivia the trivia before the first token; for a zero-token line, all of its trivia
 * @param tokens the tokens, possibly empty
 */
public record LogicalLine(int line, String source, List<Trivia> trivia, List<Token> tokens) {

    /** False for a blank or comment-only line. */
    public boolean hasTokens() {
        return !tokens.isEmpty();
    }

    @Override
    public String toString() {
        return line + ": " + tokens;
    }
}
