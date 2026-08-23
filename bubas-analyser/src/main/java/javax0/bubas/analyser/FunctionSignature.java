package javax0.bubas.analyser;

import javax0.bubas.api.BubasDefinitionException;
import javax0.bubas.api.BubasType;
import javax0.bubas.api.Context;

import java.util.List;

/**
 * A function, with its signature read off the Java method rather than declared twice.
 *
 * @param name           as registered; reserved case-insensitively but written as registered
 * @param returnType     {@code VOID} for a procedure
 * @param parameters     in order, without the leading context
 * @param implementation the class and method to call
 */
public record FunctionSignature(String name, BubasType returnType, List<Parameter> parameters,
                                Implementation implementation) {

    public record Parameter(String name, BubasType type) {
    }

    static FunctionSignature derive(String name, Implementation implementation, JavaTypes types) {
        final var where = "function " + name;
        final var method = implementation.method();
        final var javaParameters = method.getParameterTypes();
        if (javaParameters.length == 0 || !Context.class.isAssignableFrom(javaParameters[0])) {
            throw new BubasDefinitionException(where + ": " + method.getName()
                    + " must take a Context as its first parameter");
        }
        final var names = implementation.parameterNames();
        final var parameters = new java.util.ArrayList<Parameter>();
        for (int i = 1; i < javaParameters.length; i++) {
            parameters.add(new Parameter(names.get(i),
                    types.of(javaParameters[i], where + ", parameter " + names.get(i))));
        }
        return new FunctionSignature(name,
                types.of(method.getReturnType(), where + ", return type"),
                List.copyOf(parameters), implementation);
    }

    /** How the function reads in a diagnostic: {@code LOAD_ORDER(orderId INTEGER) -> Order}. */
    @Override
    public String toString() {
        return name + parameters.stream()
                .map(p -> p.name() + " " + p.type())
                .reduce((a, b) -> a + ", " + b)
                .map(s -> "(" + s + ")")
                .orElse("()")
                + (returnType == BubasType.VOID ? "" : " -> " + returnType);
    }
}
