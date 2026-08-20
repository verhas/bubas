package javax0.bubas.lexer;

/** The kinds of text that carry no meaning for the language but must survive for tooling. */
public enum TriviaType {
    /** A run of spaces and tabs. */
    WHITESPACE,
    /** An apostrophe and everything after it on that physical line. */
    COMMENT,
    /** A lone underscore that continued a physical line. */
    CONTINUATION,
    /** A line terminator, exactly as written: {@code \n}, {@code \r\n} or {@code \r}. */
    NEWLINE
}
