/**
 * The statements a BUBAS unit test is written with: mock declarations, the arguments the subject
 * runs with, the act, and the expectations.
 * <p>
 * It depends only on {@code bubas.api}, which is the point of separating it from the runner. A
 * handler that could see the compiler would eventually reach for it; this way the only thing a
 * BUNIT statement can do is ask the {@code MockRecorder} it is given, through an interface the
 * runner implements.
 */
module bubas.bunit.commands {
    requires bubas.api;
    exports javax0.bubas.bunit.commands;
}
