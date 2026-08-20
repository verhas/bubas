package javax0.bubas.api;

/**
 * The single failure type of BUBAS. Every compilation error and every runtime failure is reported
 * as a {@code BubasException} carrying the logical line it occurred on and that line's source
 * text.
 * <p>
 * It is unchecked. A checked exception would force {@code throws} clauses through every
 * implementation method, and {@code Context.error(String)} is meant to be called from anywhere.
 * <p>
 * {@link #getMessage()} returns the bare message, as specified. {@link #toString()} adds the
 * position so stack traces stay useful, and {@link #getDiagnostic()} renders the multi-line form
 * intended for a human reading a build log.
 */
public class BubasException extends RuntimeException {

    private final int line;
    private final String sourceLine;

    public BubasException(String message, int line, String sourceLine) {
        this(message, line, sourceLine, null);
    }

    public BubasException(String message, int line, String sourceLine, Throwable cause) {
        super(message, cause);
        this.line = line;
        this.sourceLine = sourceLine;
    }

    /** The 1-based number of the line the failure is attributed to. */
    public int getLine() {
        return line;
    }

    /** The source text of the offending line. */
    public String getSourceLine() {
        return sourceLine;
    }

    /**
     * The message with its position and source text, as it should appear in a build log.
     */
    public String getDiagnostic() {
        final var sb = new StringBuilder("line ").append(line).append(": ").append(getMessage());
        for (final var physical : sourceLine.split("\n", -1)) {
            sb.append("\n    ").append(physical);
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return getClass().getName() + ": line " + line + ": " + getMessage();
    }
}
