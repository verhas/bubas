/**
 * The statements a BUBAS unit test is written with: mock declarations, the arguments the subject
 * runs with, the act, and the expectations.
 * <p>
 * One DSL over {@code bubas.bunit}, not part of it. Every statement here reaches the framework
 * through {@code MockRecorder} and declares what it does through the framework's interfaces, so the
 * framework never recognises a statement by its name. A different vocabulary — terser, translated,
 * shaped for one domain — is a different module and nothing else changes.
 * <p>
 * It cannot see the analyser, so a statement cannot compile anything, reach an interpreter, or look
 * at the language under test.
 */
module bubas.bunit.commands {
    requires bubas.api;
    requires bubas.bunit;
    exports javax0.bubas.bunit.commands;
}
