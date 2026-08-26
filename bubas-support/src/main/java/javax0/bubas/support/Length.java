package javax0.bubas.support;

import javax0.bubas.api.BubasDescription;
import javax0.bubas.api.BubasArray;
import javax0.bubas.api.Context;

/**
 * {@code LENGTH(a) -> INTEGER}
 * <p>
 * The one place {@code ANY_ARRAY} earns its keep: an array's length is the same question whatever
 * its elements are, so declaring it per element type would be five functions for one idea.
 */
@BubasDescription("""
        How many elements an array has, whatever its element type.
        """)
public final class Length {

    public static final String NAME = "LENGTH";

    public long call(Context ctx, BubasArray a) {
        return a.size();
    }
}
