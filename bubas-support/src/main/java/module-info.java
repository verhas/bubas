/**
 * The statements and functions a language gets from the standard module rather than from its
 * embedder. Nothing here is privileged: these are ordinary patterns with ordinary implementations,
 * and an embedder that wants different ones simply does not install these.
 * <p>
 * It depends only on {@code bubas.api}, which is the point of splitting the API out — a library of
 * functions must not have to depend on the interpreter.
 */
module bubas.support {
    requires bubas.api;
    exports javax0.bubas.support;
}
