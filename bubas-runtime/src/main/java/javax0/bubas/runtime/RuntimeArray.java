package javax0.bubas.runtime;

import javax0.bubas.api.BubasArray;
import javax0.bubas.api.BubasType;

import java.lang.reflect.Array;

/**
 * An array handed to a parameter declared {@code ANY_ARRAY}, whose element type the function does
 * not know. Every array whose element type <em>is</em> known crosses as a native Java array, so
 * this wrapper appears only where the declaration asked for it.
 */
record RuntimeArray(Object raw, BubasType elementType) implements BubasArray {

    @Override
    public int size() {
        return Array.getLength(raw);
    }
}
