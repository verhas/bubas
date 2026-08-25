package javax0.bubas.api;

import java.util.List;
import java.util.Map;

/**
 * Substitutes behaviour for a function or a command at the point the interpreter would call its
 * implementation.
 * <p>
 * This exists for the testing framework and for nothing else. It is part of the interpreter's API
 * because a test framework lives outside the runtime, not because embedders are expected to reach
 * for it: an interceptor bypasses the implementations a language was sealed with, which is exactly
 * what a mock needs and exactly what production code must not do.
 * <p>
 * Interception is installed on an interpreter, never on a language, so a sealed language knows
 * nothing about testing and the same {@link BubasType}-checked program runs in both modes. Not
 * installing one runs the real implementations, which is what makes an integration mode free.
 * <p>
 * A command receives the same argument objects its handler would have — a {@link VariableArg} for
 * a {@code var} placeholder, an {@link ExpressionArg} for an expression, and so on — so an
 * interceptor can write what the real command would have written. That matters: a command whose
 * pattern assigns a variable leaves the script reading an unassigned slot if a mock merely records
 * the call and returns.
 */
public interface BubasCallInterceptor {

    /** Whether calls to this function are handled here instead of by its implementation. */
    default boolean interceptsFunction(String name) {
        return false;
    }

    /**
     * The value the function yields, in place of calling it.
     *
     * @param arguments every argument, evaluated, in source order. A variadic call presents its
     *                  spread arguments individually rather than packed
     * @return the result, or {@code null} for a function whose return type is {@code VOID}
     */
    default Value onFunction(String name, List<Value> arguments) {
        throw new UnsupportedOperationException("no interception for function " + name);
    }

    /** Whether the command written with this pattern is handled here instead of by its handler. */
    default boolean interceptsCommand(String pattern) {
        return false;
    }

    /**
     * Runs in place of the command's handler.
     *
     * @param arguments the handler's own arguments, keyed by placeholder name
     */
    default void onCommand(String pattern, StatementContext context, Map<String, Object> arguments) {
        throw new UnsupportedOperationException("no interception for command " + pattern);
    }
}
