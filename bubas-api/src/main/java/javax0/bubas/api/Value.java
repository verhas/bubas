package javax0.bubas.api;

import java.math.BigDecimal;

/** A BUBAS value, with the conversions its type admits. */
public interface Value {

    BubasType type();

    long asLong();

    BigDecimal asDecimal();

    String asString();

    boolean asBoolean();

    /**
     * Checked against the registered opaque type rather than blind-casting, so a mismatch produces
     * a BUBAS diagnostic instead of a {@link ClassCastException}.
     */
    <T> T as(Class<T> javaType);
}
