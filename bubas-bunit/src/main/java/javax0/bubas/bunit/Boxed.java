package javax0.bubas.bunit;

import javax0.bubas.api.BubasType;
import javax0.bubas.api.Value;
import javax0.bubas.api.TypeNames;

import java.math.BigDecimal;

/** A value BUNIT constructs itself — a token, mostly, which no interpreter would have made. */
record Boxed(BubasType type, Object raw) implements Value {

    @Override
    public long asLong() {
        return expect(Long.class, TypeNames.INTEGER);
    }

    @Override
    public BigDecimal asDecimal() {
        return expect(BigDecimal.class, TypeNames.DECIMAL);
    }

    @Override
    public String asString() {
        return expect(String.class, TypeNames.STRING);
    }

    @Override
    public boolean asBoolean() {
        return expect(Boolean.class, TypeNames.BOOLEAN);
    }

    @Override
    public <T> T as(Class<T> javaType) {
        return javaType.cast(raw);
    }

    private <T> T expect(Class<T> wanted, String named) {
        if (!wanted.isInstance(raw)) {
            throw new IllegalStateException("a " + type + " cannot be read as " + named);
        }
        return wanted.cast(raw);
    }
}
