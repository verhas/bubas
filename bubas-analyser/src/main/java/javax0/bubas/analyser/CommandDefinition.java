package javax0.bubas.analyser;

import javax0.bubas.analyser.pattern.ConstraintResolver;
import javax0.bubas.analyser.pattern.Kind;
import javax0.bubas.analyser.pattern.Placeholder;
import javax0.bubas.analyser.pattern.ResolvedConstraint;
import javax0.bubas.analyser.pattern.StatementPattern;
import javax0.bubas.api.BubasDefinitionException;
import javax0.bubas.api.BubasType;
import javax0.bubas.api.ExpressionArg;
import javax0.bubas.api.StatementContext;
import javax0.bubas.api.Value;
import javax0.bubas.api.VariableArg;

/**
 * A statement pattern together with the class that implements it.
 */
public record CommandDefinition(StatementPattern pattern, Implementation implementation) {

    static CommandDefinition of(StatementPattern pattern, Implementation implementation,
                                ConstraintResolver constraints) {
        final var where = "command \"" + pattern + "\"";
        final var method = implementation.method();
        final var javaParameters = method.getParameterTypes();
        if (javaParameters.length == 0
                || !StatementContext.class.isAssignableFrom(javaParameters[0])) {
            throw new BubasDefinitionException(where + ": " + method.getName()
                    + " must take a StatementContext as its first parameter");
        }
        final var placeholders = pattern.placeholders();
        if (javaParameters.length - 1 != placeholders.size()) {
            throw new BubasDefinitionException(where + ": the pattern has " + placeholders.size()
                    + " placeholder(s) but " + method.getName() + " takes "
                    + (javaParameters.length - 1) + " beside the context");
        }
        for (int i = 0; i < placeholders.size(); i++) {
            check(where, pattern, placeholders.get(i), javaParameters[i + 1], constraints);
        }
        return new CommandDefinition(pattern, implementation);
    }

    /**
     * Most kinds fix their parameter type by kind alone. A literal's follows from its constraint,
     * which is why this needs the resolver: {@code /INTEGER} means {@code long}, while an
     * unconstrained literal or one that only narrows to NUMBER can be any constant and so arrives
     * as a {@link Value}.
     */
    private static void check(String where, StatementPattern pattern, Placeholder placeholder,
                              Class<?> javaType, ConstraintResolver constraints) {
        final var expected = switch (placeholder.kind()) {
            case VAR, IDENTIFIER -> VariableArg.class;
            case EXPRESSION -> ExpressionArg.class;
            case TYPE -> BubasType.class;
            case LITERAL -> literalType(where, pattern, placeholder, constraints);
        };
        if (!expected.isAssignableFrom(javaType)) {
            throw new BubasDefinitionException(where + ": placeholder '" + placeholder.name()
                    + "' is a " + placeholder.kind().spelling()
                    + (placeholder.kind() == Kind.LITERAL && placeholder.constraint() != null
                    ? " constrained to " + placeholder.constraint() : "")
                    + ", so its parameter must be " + expected.getSimpleName()
                    + ", not " + javaType.getSimpleName());
        }
    }

    private static Class<?> literalType(String where, StatementPattern pattern,
                                        Placeholder placeholder, ConstraintResolver constraints) {
        if (placeholder.constraint() == null) {
            return Value.class;
        }
        return switch (constraints.resolve(pattern, placeholder.constraint())) {
            case ResolvedConstraint.Type(var type, var ignored) -> {
                if (type instanceof BubasType.Opaque) {
                    throw new BubasDefinitionException(where + ": placeholder '"
                            + placeholder.name() + "' is a literal constrained to the opaque type "
                            + type + ", but a constant is never opaque");
                }
                yield type.javaType();
            }
            case ResolvedConstraint.Array ignored -> throw new BubasDefinitionException(where
                    + ": placeholder '" + placeholder.name()
                    + "' is a literal constrained to an array, but a constant is never an array");
            default -> Value.class;
        };
    }
}
