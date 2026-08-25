package javax0.bubas.bunit.commands;

import javax0.bubas.api.Value;

import java.util.List;
import java.util.Optional;

/**
 * What a BUNIT statement is allowed to ask of the machinery running the test.
 * <p>
 * This interface is the whole reason the statements live in their own module. A test statement can
 * declare a mock, supply an argument, start the subject and ask what happened — and nothing else.
 * It cannot compile, cannot reach an interpreter, and cannot see the language the subject was
 * written against, because none of that is on this interface and the module cannot see those types
 * at all.
 * <p>
 * The runner implements it. A statement reaches its instance through
 * {@link javax0.bubas.api.Context#service(Class)}, which is the ordinary way a handler reaches
 * anything the embedder supplied.
 * <p>
 * Reporting a failed expectation is deliberately <em>not</em> here: a statement raises it through
 * its own {@link javax0.bubas.api.StatementContext#error(String)}, so the diagnostic carries the
 * line of the expectation that failed rather than a line inside the framework.
 */
public interface MockRecorder {

    /**
     * Declares what a function answers.
     *
     * @param arguments the arguments this mock matches, or empty to answer whatever it is given
     * @param result    what the function yields; {@code null} for a function returning nothing
     */
    void mockFunction(String name, List<Value> arguments, Value result);

    /** Declares that a command is mocked: recorded, and its handler never runs. */
    void mockCommand(String name);

    /** Supplies one of the subject's parameters. */
    void argument(String name, Value value);

    /** Runs the subject with the mocks installed. Failure is recorded, not thrown. */
    void run();

    boolean hasRun();

    /** What the subject returned, empty when it declared no {@code RETURNS} or failed. */
    Optional<Value> result();

    /** The diagnostic the subject failed with, when it failed. */
    Optional<String> failure();

    /** Every call made to this function or command, in order, each with its arguments. */
    List<List<Value>> callsTo(String name);
}
