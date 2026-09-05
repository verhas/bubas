package javax0.bubas.runtime;

/**
 * What one run may spend.
 * <p>
 * A rule is written by somebody who does not think about budgets, run by an application that has
 * to. Neither limit changes what a program means: a run that stays inside them cannot tell they
 * are there, and a run that does not is stopped rather than answered wrongly.
 *
 * @param steps       how many statements and loop passes the run may take, together
 * @param arrayLength the largest array a command may bring into existence
 */
record Limits(long steps, int arrayLength) {

    /** What a run gets when the embedder says nothing: as much as the machine will give it. */
    static final Limits NONE = new Limits(Long.MAX_VALUE, Integer.MAX_VALUE);

    Limits withSteps(long steps) {
        return new Limits(steps, arrayLength);
    }

    Limits withArrayLength(int arrayLength) {
        return new Limits(steps, arrayLength);
    }
}
