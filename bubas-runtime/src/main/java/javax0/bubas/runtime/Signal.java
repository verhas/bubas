package javax0.bubas.runtime;

/**
 * Non-local exits, as exceptions because a tree walk has no other way out of nested calls.
 * <p>
 * They carry no stack trace: they are control flow, not failures, and a program may raise thousands
 * of them in a loop.
 */
abstract sealed class Signal extends RuntimeException {

    private Signal() {
        super(null, null, false, false);
    }

    /** {@code RETURN}, leaving the program. */
    static final class Returned extends Signal {
        private final Object value;

        Returned(Object value) {
            this.value = value;
        }

        Object value() {
            return value;
        }
    }

    /** {@code EXIT}, leaving the loop lowering resolved it to. */
    static final class Broke extends Signal {
        private final int loopId;

        Broke(int loopId) {
            this.loopId = loopId;
        }

        int loopId() {
            return loopId;
        }
    }
}
