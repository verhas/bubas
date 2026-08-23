package javax0.bubas.runtime;

import javax0.bubas.api.BubasType;
import javax0.bubas.api.Value;

import java.math.BigDecimal;

/**
 * A value handed to embedder code.
 * <p>
 * The raw form is the Java type the BUBAS type maps to, so a function receiving it gets what it
 * declared. The conversions are deliberately not lenient: a value is what its type says, and asking
 * for something else is a mistake worth a diagnostic rather than a silent coercion.
 */
record RuntimeValue(BubasType type, Object raw) implements Value {

    @Override
    public long asLong() {
        return expect(Long.class, "INTEGER");
    }

    @Override
    public BigDecimal asDecimal() {
        return expect(BigDecimal.class, "DECIMAL");
    }

    @Override
    public String asString() {
        return expect(String.class, "STRING");
    }

    @Override
    public boolean asBoolean() {
        return expect(Boolean.class, "BOOLEAN");
    }

    /** Checked against the value's own type, so a mismatch is a diagnostic, not a ClassCastException. */
    @Override
    public <T> T as(Class<T> javaType) {
        if (raw != null && !javaType.isInstance(raw)) {
            throw new Mistake("a " + type + " cannot be read as " + javaType.getSimpleName());
        }
        return javaType.cast(raw);
    }

    private <T> T expect(Class<T> wanted, String named) {
        if (!wanted.isInstance(raw)) {
            throw new Mistake("a " + type + " cannot be read as " + named);
        }
        return wanted.cast(raw);
    }
}
