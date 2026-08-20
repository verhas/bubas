/**
 * Turns BUBAS source text into logical lines of tokens. Everything about physical line structure —
 * comments, continuation, line joining — is resolved here, so no later stage ever sees a physical
 * line.
 */
module bubas.lexer {
    requires bubas.api;
    exports javax0.bubas.lexer;
}
