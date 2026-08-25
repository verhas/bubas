package javax0.bubas.bunit;

import javax0.bubas.api.BubasType;

/**
 * An opaque value that stands for a domain object without being one.
 * <p>
 * A script may hold an opaque value, pass it and store it in an array, and may never look inside —
 * every operation that would reveal anything is a function the embedder supplies, and in a test
 * those are mocked too. So a mock never has to produce a real {@code Order}: identity is the whole
 * of what the subject can observe, and a token has that.
 * <p>
 * The consequence, worth knowing before it surprises someone: a token reaching a handler that was
 * <em>not</em> mocked fails, because it is not an instance of the class that handler declared. The
 * opaque-valued surface has to be mocked as a whole.
 *
 * @param name as the test wrote it, so a diagnostic can say which token it meant
 */
public record Token(String name) {

    /**
     * Whether a value of {@code given} type, written where {@code expected} is wanted, names a
     * token rather than being one.
     * <p>
     * A STRING where an opaque value belongs can only be a token name: opaque values are the sole
     * kind BUBAS cannot construct, so nothing else could have been meant. The checker and the
     * recorder both need this rule, and it is one rule so that they cannot disagree about it.
     */
    public static boolean named(BubasType expected, BubasType given) {
        return expected instanceof BubasType.Opaque && given == BubasType.STRING;
    }

    @Override
    public String toString() {
        return name;
    }
}
