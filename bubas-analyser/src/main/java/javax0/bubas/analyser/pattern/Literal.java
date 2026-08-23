package javax0.bubas.analyser.pattern;

import javax0.bubas.lexer.TokenType;

/**
 * A fixed token in a pattern. Produced by lexing the pattern's literal text with the same lexer
 * that reads source, so a literal can only be something a source line could actually contain.
 * <p>
 * A {@link TokenType#WORD} literal matches case-insensitively and becomes a reserved word;
 * anything else matches exactly and reserves nothing.
 */
public record Literal(TokenType type, String text) implements PatternElement {

    public boolean isWord() {
        return type == TokenType.WORD;
    }
}
