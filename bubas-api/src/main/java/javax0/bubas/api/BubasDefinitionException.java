package javax0.bubas.api;

/**
 * Raised when the embedder defines the language wrongly: a malformed statement pattern, an
 * implementation class that does not match its declared signature, a name collision, two patterns
 * that could match the same line.
 * <p>
 * It is deliberately distinct from {@link BubasException}. A {@code BubasException} always points
 * at a line of BUBAS source and is aimed at the script author; this one points at Java code and is
 * aimed at the developer embedding BUBAS. They are raised at different times — registration versus
 * compilation — and read by different people.
 */
public class BubasDefinitionException extends RuntimeException {

    public BubasDefinitionException(String message) {
        super(message);
    }

    public BubasDefinitionException(String message, Throwable cause) {
        super(message, cause);
    }
}
