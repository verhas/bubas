/**
 * Executes a compiled program by walking its core tree.
 * <p>
 * Every decision the language makes was made during lowering, so this module implements a fixed set
 * of named operations rather than a specification. A code generator for another target implements
 * the same set — which is what keeps interpreted and compiled execution from drifting apart.
 */
module bubas.runtime {
    requires bubas.api;
    requires bubas.analyser;
    requires bubas.lexer;
    exports javax0.bubas.runtime;
}
