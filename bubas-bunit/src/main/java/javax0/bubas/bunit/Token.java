package javax0.bubas.bunit;

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

    @Override
    public String toString() {
        return name;
    }
}
