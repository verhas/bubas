/**
 * Everything that happens before execution: statement patterns, the parser, the type checker and
 * definite assignment. {@code BubasLanguage} lives here because it owns {@code compile()}.
 * <p>
 * The pattern matcher shares this module with the parser deliberately. Separating them would need
 * an interface module and runtime injection to solve a dependency that does not exist: every
 * pattern, function and opaque type is registered and the language sealed before the first source
 * line is matched, so the parser's vocabulary is complete and immutable by the time it runs.
 */
module bubas.analyser {
    requires bubas.api;
    requires bubas.lexer;
    exports javax0.bubas.analyser;
    exports javax0.bubas.analyser.pattern;
}
