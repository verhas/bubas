package javax0.bubas.support;

import javax0.bubas.api.BubasDescription;
import javax0.bubas.api.BubasType;
import javax0.bubas.api.ExpressionArg;
import javax0.bubas.api.StatementContext;
import javax0.bubas.api.VariableArg;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.util.Arrays;

/**
 * {@code DECLARE numbers[5] INTEGER}
 * <p>
 * The array is a native Java array of the element type, which is what a function receiving it gets
 * — no wrapper, no copy. Java zero-fills numbers and booleans and null-fills references, so only
 * {@code STRING} and {@code DECIMAL} need filling to reach the defaults the language promises.
 * An opaque array is left null-filled: null is a perfectly ordinary value for one, and the
 * language has no way to make anything else.
 */
@BubasDescription("""
        Brings an array into existence with a size worked out as the program runs.
        Elements start at zero, empty text or false, according to the type. The size is fixed once
        the array exists; there is no growing it.
        """)
public final class DeclareArray {

    public static final String PATTERN = "DECLARE {new > identifier/ARRAY/T:name > initialized}"
            + "[{expression/INTEGER:size}] {type:T}";

    public void call(StatementContext ctx, VariableArg name, ExpressionArg size, BubasType type) {
        final long length = size.evaluate().asLong();
        if (length < 0) {
            ctx.error("an array cannot have " + length + " elements");
        }
        if (length > Integer.MAX_VALUE) {
            ctx.error("an array of " + length + " elements is larger than this runtime can hold");
        }
        final var array = Array.newInstance(type.javaType(), (int) length);
        if (array instanceof String[] strings) {
            Arrays.fill(strings, "");
        } else if (array instanceof BigDecimal[] decimals) {
            Arrays.fill(decimals, BigDecimal.ZERO);
        }
        name.set(array);
    }
}
