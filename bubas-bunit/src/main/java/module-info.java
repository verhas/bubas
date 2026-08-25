/**
 * The BUBAS mocking framework: it records what a test declared, answers for it while the subject
 * runs, and reports what happened.
 * <p>
 * It knows nothing about the statements a test is written with. The vocabulary is a separate module
 * that depends on this one, and the framework interrogates a command's implementation class through
 * the interfaces here rather than recognising it by name — the same way BUBAS treats {@code DECLARE}
 * as an ordinary pattern rather than a built-in. Another DSL could replace ours without this module
 * changing.
 */
module bubas.bunit {
    requires bubas.api;
    requires bubas.analyser;
    requires bubas.runtime;
    exports javax0.bubas.bunit;
}
