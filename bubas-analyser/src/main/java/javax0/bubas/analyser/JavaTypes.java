package javax0.bubas.analyser;

import javax0.bubas.api.BubasArray;
import javax0.bubas.api.BubasDefinitionException;
import javax0.bubas.api.BubasType;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Maps a Java type to the BUBAS type it stands for, which is what lets a function's signature be
 * derived from its Java method rather than declared twice.
 * <p>
 * The opaque half needs the registry, and the registry must be one-to-one: because a Java class
 * identifies a BUBAS type, two type names registered against one class would make the mapping
 * ambiguous, so that is rejected when the type is registered.
 */
final class JavaTypes {

    private static final Map<Class<?>, BubasType> SCALARS = Map.of(
            long.class, BubasType.INTEGER,
            BigDecimal.class, BubasType.DECIMAL,
            String.class, BubasType.STRING,
            boolean.class, BubasType.BOOLEAN,
            void.class, BubasType.VOID,
            BubasArray.class, BubasType.ANY_ARRAY);

    private final Map<Class<?>, BubasType.Opaque> opaqueTypes;

    JavaTypes(Map<Class<?>, BubasType.Opaque> opaqueTypes) {
        this.opaqueTypes = opaqueTypes;
    }

    BubasType of(Class<?> javaType, String where) {
        final var scalar = SCALARS.get(javaType);
        if (scalar != null) {
            return scalar;
        }
        final var opaque = opaqueTypes.get(javaType);
        if (opaque != null) {
            return opaque;
        }
        if (javaType.isArray()) {
            return BubasType.arrayOf(of(javaType.getComponentType(), where));
        }
        throw new BubasDefinitionException(where + ": " + javaType.getTypeName()
                + " is not a BUBAS type. Expected long, BigDecimal, String, boolean, "
                + "an array of those, BubasArray, or a registered opaque type"
                + (Integer.class == javaType || int.class == javaType
                ? " — INTEGER is 64-bit, so use long" : ""));
    }
}
