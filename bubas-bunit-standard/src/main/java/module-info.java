/**
 * BUNIT assembled: the module an application depends on to test its BUBAS programs.
 * <p>
 * The framework and the statements are deliberately separate — one is the mocking machinery, the
 * other is a vocabulary over it, and neither knows the other by name. Someone has to put them
 * together, though, and it should not be the embedder's first task. That is this module: it seals
 * the standard test language and hands out a runner.
 * <p>
 * An embedder wanting a different vocabulary skips this module and assembles its own, which is the
 * whole point of the split being here rather than inside the framework.
 */
module bubas.bunit.standard {
    requires transitive bubas.api;
    requires transitive bubas.bunit;
    requires bubas.bunit.commands;
    requires transitive bubas.bunit.matchers;
    requires bubas.analyser;
    requires bubas.support;
    exports javax0.bubas.bunit.standard;
}
