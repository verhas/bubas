package javax0.bubas.api;

import java.lang.reflect.Array;
import java.math.BigDecimal;

/**
 * A BUBAS type.
 * <p>
 * Every type knows the Java type it corresponds to, which is what lets a function's BUBAS signature
 * be derived from its Java method rather than declared twice, and what lets an argument be passed
 * without boxing through a generic value.
 * <p>
 * {@code NUMBER} is deliberately absent: it constrains a pattern placeholder to {@code INTEGER} or
 * {@code DECIMAL} but is not a type anything can have.
 */
public sealed interface BubasType {

    BubasType INTEGER = Scalar.INTEGER;
    BubasType DECIMAL = Scalar.DECIMAL;
    BubasType STRING = Scalar.STRING;
    BubasType BOOLEAN = Scalar.BOOLEAN;
    /** A function return type only; no value ever has it. */
    BubasType VOID = Scalar.VOID;
    /** An array of any element type. Legal in parameter position only. */
    BubasType ANY_ARRAY = Wildcard.ANY_ARRAY;

    /** Any value; a function parameter only, never a return type. */
    BubasType ANY = Wildcard.ANY;

    static BubasType opaque(String name, Class<?> javaType) {
        return new Opaque(name, javaType);
    }

    static BubasType arrayOf(BubasType element) {
        return new ArrayOf(element);
    }

    /** The Java type a value of this type has. */
    Class<?> javaType();

    /**
     * True when a value of {@code source} may be assigned to a target of this type: the same type,
     * an {@code INTEGER} widening to a {@code DECIMAL}, or an opaque type whose Java class is
     * assignable to this one's.
     */
    boolean accepts(BubasType source);

    enum Scalar implements BubasType {
        INTEGER(long.class),
        DECIMAL(BigDecimal.class),
        STRING(String.class),
        BOOLEAN(boolean.class),
        VOID(void.class);

        private final Class<?> javaType;

        Scalar(Class<?> javaType) {
            this.javaType = javaType;
        }

        @Override
        public Class<?> javaType() {
            return javaType;
        }

        @Override
        public boolean accepts(BubasType source) {
            return this == source || (this == DECIMAL && source == INTEGER);
        }
    }

    /** A registered opaque type: a Java class BUBAS can hold and pass but never inspect. */
    record Opaque(String name, Class<?> javaType) implements BubasType {

        /**
         * Widening follows Java, interfaces included, so BUBAS accepts exactly what Java would.
         * Nothing is computed or cached: the registered classes carry the lattice already.
         */
        @Override
        public boolean accepts(BubasType source) {
            return source instanceof Opaque(var ignored, var sourceJava)
                    && javaType.isAssignableFrom(sourceJava);
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * An array of a known element type. Arrays are invariant: an array of a subtype is not an array
     * of its supertype, because a handler holding the backing store could otherwise write an
     * element the script's declared type forbids.
     */
    record ArrayOf(BubasType element) implements BubasType {

        @Override
        public Class<?> javaType() {
            return Array.newInstance(element.javaType(), 0).getClass();
        }

        @Override
        public boolean accepts(BubasType source) {
            return source instanceof ArrayOf(var sourceElement) && element.equals(sourceElement);
        }

        @Override
        public String toString() {
            return element + "[]";
        }
    }

    /**
     * Types that stand for a family rather than for one thing. Both exist only in a function
     * signature, derived from a Java parameter: neither can be written in BUBAS source, held by a
     * variable, or returned. A wildcard that could be returned would put a value of unknown type
     * into the script, where nothing downstream could check it.
     */
    enum Wildcard implements BubasType {
        /** Any array, whatever its element type. {@code LENGTH} is the reason it exists. */
        ANY_ARRAY {
            @Override
            public Class<?> javaType() {
                return BubasArray.class;
            }

            @Override
            public boolean accepts(BubasType source) {
                return source instanceof ArrayOf;
            }

            @Override
            public String toString() {
                return TypeNames.ARRAY;
            }
        },

        /**
         * Any value at all. The parameter arrives as a {@link Value}, which carries its own
         * {@link Value#type()} — so the handler is told what it was given rather than having to
         * guess, and the script's static typing is untouched because nothing comes back out.
         */
        ANY {
            @Override
            public Class<?> javaType() {
                return Value.class;
            }

            @Override
            public boolean accepts(BubasType source) {
                return source != Scalar.VOID;
            }

            @Override
            public String toString() {
                return TypeNames.ANY;
            }
        }
    }
}
