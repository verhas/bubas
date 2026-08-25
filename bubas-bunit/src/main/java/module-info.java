/**
 * Runs a BUBAS unit test. It compiles the test against the fixed BUNIT language, checks that its
 * mocks are complete on every path, then runs the subject against the embedder's own language with
 * the mocks installed as a {@code BubasCallInterceptor}.
 */
module bubas.bunit {
    requires bubas.api;
    requires bubas.bunit.commands;
    requires bubas.analyser;
    requires bubas.runtime;
    requires bubas.support;
    exports javax0.bubas.bunit;
}
