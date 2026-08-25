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
 * @param parameters     in order, without the leading context. When {@code varargs}, the last one
 *                       carries the <em>element</em> type rather than the array type
 * @param varargs        the Java method is variadic, so the last parameter absorbs every remaining
 *                       argument
 * @param implementation the class and method to call
 */
public record FunctionSignature(String name, BubasType returnType, List<Parameter> parameters,
                                boolean varargs, Implementation implementation) {

    public record Parameter(String name, BubasType type) {
    }

    /** How many arguments a call must have; every further one goes to the variadic parameter. */
    public int required() {
        return varargs ? parameters.size() - 1 : parameters.size();
    }

    /** Whether a call giving this many arguments has the right number of them. */
    public boolean accepts(int given) {
        return varargs ? given >= required() : given == parameters.size();
    }

    /** The type expected at one argument position, which repeats once past a variadic parameter. */
    public BubasType typeOf(int index) {
        return parameters.get(Math.min(index, parameters.size() - 1)).type();
    }

    /** The name to blame at one argument position. */
    public String nameOf(int index) {
        return parameters.get(Math.min(index, parameters.size() - 1)).name();
    }

    /** The complaint when a call has the wrong number of arguments. */
    public String arityComplaint(int given) {
        return name + " takes " + (varargs ? "at least " : "") + required()
                + " argument(s) but was given " + given + ": " + this;
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
        final var varargs = method.isVarArgs();
        final var parameters = new java.util.ArrayList<Parameter>();
        for (int i = 1; i < javaParameters.length; i++) {
            // A variadic parameter is declared as an array; BUBAS records what one argument is.
            final var javaType = varargs && i == javaParameters.length - 1
                    ? javaParameters[i].getComponentType()
                    : javaParameters[i];
            parameters.add(new Parameter(names.get(i),
                    types.of(javaType, where + ", parameter " + names.get(i))));
        }
        final var returnType = types.of(method.getReturnType(), where + ", return type");
        if (returnType == BubasType.ANY || returnType == BubasType.ANY_ARRAY) {
            throw new BubasDefinitionException(where + ": " + method.getName() + " returns "
                    + returnType + ", but a wildcard may only be a parameter. A returned value"
                    + " enters the script, where its type has to be known to check anything;"
                    + " return the concrete type instead.");
        }
        return new FunctionSignature(name, returnType,
                List.copyOf(parameters), varargs, implementation);
    }

    /**
     * How the function reads in a diagnostic: {@code LOAD_ORDER(orderId INTEGER) -> Order}, and
     * {@code JOIN(parts STRING...) -> STRING} when variadic.
     */
    @Override
    public String toString() {
        final var last = parameters.size() - 1;
        return name + java.util.stream.IntStream.range(0, parameters.size())
                .mapToObj(i -> parameters.get(i).name() + " " + parameters.get(i).type()
                        + (varargs && i == last ? "..." : ""))
                .reduce((a, b) -> a + ", " + b)
                .map(s -> "(" + s + ")")
                .orElse("()")
                + (returnType == BubasType.VOID ? "" : " -> " + returnType);
    }
}
