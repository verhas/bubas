package javax0.bubas.analyser.pattern;

import javax0.bubas.api.BubasDefinitionException;
import javax0.bubas.api.BubasType;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Works out what each constraint's names refer to, and rejects the ones that refer to nothing.
 * <p>
 * This can only happen at {@code seal()}. When a pattern is parsed, {@code /Order} is just a word:
 * whether it names a registered opaque type is not knowable until every type is registered. The
 * parser therefore captures constraints exactly as written and leaves the meaning to here.
 * <p>
 * Resolution is total because a placeholder may not be named after a type
 * ({@link PatternParser}, checked at seal): a name is a placeholder reference when the pattern has
 * one by that name, and a type name otherwise, and the two can never both apply.
 */
public final class ConstraintResolver {

    private static final Map<String, BubasType> SCALARS = Map.of(
            "INTEGER", BubasType.INTEGER,
            "DECIMAL", BubasType.DECIMAL,
            "STRING", BubasType.STRING,
            "BOOLEAN", BubasType.BOOLEAN);

    private final Map<String, BubasType.Opaque> opaqueTypes;

    public ConstraintResolver(Map<String, BubasType.Opaque> opaqueTypes) {
        this.opaqueTypes = opaqueTypes;
    }

    /** Resolves every constraint in every pattern, throwing on the first that refers to nothing. */
    public void check(List<StatementPattern> patterns) {
        for (final var pattern : patterns) {
            for (final var placeholder : pattern.placeholders()) {
                if (placeholder.constraint() != null) {
                    resolve(pattern, placeholder.constraint());
                }
            }
        }
    }

    public ResolvedConstraint resolve(StatementPattern pattern, Constraint constraint) {
        return switch (constraint) {
            case Constraint.ArrayOf(var element) -> new ResolvedConstraint.Array(
                    element == null ? null : resolve(pattern, element));
            case Constraint.ElementOf(var name) -> element(pattern, name);
            case Constraint.Named(var name, var exact) -> named(pattern, name, exact);
        };
    }

    private ResolvedConstraint named(StatementPattern pattern, String name, boolean exact) {
        if (placeholder(pattern, name) != null) {
            return new ResolvedConstraint.Reference(name, exact);
        }
        final var canonical = name.toUpperCase(Locale.ROOT);
        if ("NUMBER".equals(canonical)) {
            if (exact) {
                throw error(pattern, "'/=NUMBER' asks for an exact match against NUMBER, which is "
                        + "not a type but a choice between INTEGER and DECIMAL");
            }
            return new ResolvedConstraint.Numeric();
        }
        final var scalar = SCALARS.get(canonical);
        if (scalar != null) {
            return new ResolvedConstraint.Type(scalar, exact);
        }
        final var opaque = opaqueTypes.get(canonical);
        if (opaque != null) {
            return new ResolvedConstraint.Type(opaque, exact);
        }
        throw error(pattern, "'" + name + "' names no placeholder in this pattern, no built-in "
                + "type, and no registered opaque type");
    }

    /**
     * {@code /a[]} needs {@code a} to be an array, and to be known as one statically — otherwise
     * nothing could say what its element type is. The referenced placeholder must therefore carry
     * an {@code ARRAY} constraint of its own.
     */
    private ResolvedConstraint element(StatementPattern pattern, String name) {
        final var referenced = placeholder(pattern, name);
        if (referenced == null) {
            throw error(pattern, "'" + name + "[]' refers to a placeholder named '" + name
                    + "', which this pattern does not have");
        }
        if (!(referenced.constraint() instanceof Constraint.ArrayOf)) {
            throw error(pattern, "'" + name + "[]' asks for the element type of '" + name
                    + "', which is not declared to be an array; constrain it with /ARRAY");
        }
        return new ResolvedConstraint.Element(name);
    }

    private static Placeholder placeholder(StatementPattern pattern, String name) {
        return pattern.placeholders().stream()
                .filter(p -> p.name().equals(name))
                .findFirst()
                .orElse(null);
    }

    private static BubasDefinitionException error(StatementPattern pattern, String message) {
        return new BubasDefinitionException("in pattern \"" + pattern + "\": " + message);
    }
}
