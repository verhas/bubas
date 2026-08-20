package javax0.bubas.lexer;

/**
 * The lexer deliberately does not distinguish keywords from identifiers. The reserved-word set is
 * not known until the language is sealed — it includes every literal token of every registered
 * pattern, every function name and every opaque type name — so classification belongs to the
 * analyser. Everything identifier-shaped is a {@link #WORD}.
 */
public enum TokenType {
    /** An identifier or a keyword; the lexer does not know which. */
    WORD,
    /** An integer literal. {@code Token.value()} holds a {@link Long}. */
    INTEGER,
    /** A decimal literal. {@code Token.value()} holds a {@link java.math.BigDecimal}. */
    DECIMAL,
    /** A string literal. {@code Token.value()} holds the unescaped content. */
    STRING,
    /** One of {@code + - * / = <> < > <= >=}. Every one of them is a binary operator. */
    OPERATOR,
    /** One of {@code ( ) [ ] , .} */
    PUNCT
}
