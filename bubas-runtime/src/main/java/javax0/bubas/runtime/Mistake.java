package javax0.bubas.runtime;

/**
 * A failure raised where no source position is known — inside a {@code Value} conversion, say,
 * called from an embedder's handler.
 * <p>
 * It never escapes: the dispatcher wraps it with the line of the statement that called the handler,
 * which is the only place that knows one. That is why {@code BubasException} has no position-less
 * constructor — every one that reaches the embedder points at a line.
 */
final class Mistake extends RuntimeException {

    Mistake(String message) {
        super(message);
    }
}
