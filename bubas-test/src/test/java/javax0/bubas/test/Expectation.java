package javax0.bubas.test;

import java.util.List;

/**
 * What a script's header says should happen to it.
 * <p>
 * The header is comments, so a script carrying one is still a valid script — nothing in the corpus
 * needs a companion file, and a reader opening a {@code .bu} sees immediately what it is for.
 *
 * @param outcome     from the first line
 * @param what        the prose explaining what the script tests
 * @param message     a fragment the diagnostic must contain, or {@code null} when unstated
 * @param line        the line the diagnostic must name, or {@code null} when unstated
 * @param maxSteps    the step budget to run it under, or {@code null} for none
 * @param maxArray    the array limit to run it under, or {@code null} for none
 */
record Expectation(Outcome outcome, String what, String message, Integer line,
                   Long maxSteps, Integer maxArray) {

    enum Outcome {
        /** The script must fail to compile. */
        NO_COMPILE("'NO-COMPILE"),
        /** The script must compile and then fail while running. */
        RUN_TIME_ERROR("'RUN-TIME-ERROR"),
        /** The script must compile and run to the end without error. */
        OK("'OK");

        private final String directive;

        Outcome(String directive) {
            this.directive = directive;
        }

        static Outcome of(String line) {
            for (final var outcome : values()) {
                if (outcome.directive.equals(line.trim())) {
                    return outcome;
                }
            }
            throw new IllegalArgumentException("a script starts with 'NO-COMPILE, 'RUN-TIME-ERROR "
                    + "or 'OK, not with \"" + line + "\"");
        }
    }

    static Expectation of(List<String> lines) {
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("an empty script");
        }
        final var outcome = Outcome.of(lines.getFirst());
        final var what = new StringBuilder();
        String message = null;
        Integer line = null;
        Long maxSteps = null;
        Integer maxArray = null;
        for (final var text : lines.subList(1, lines.size())) {
            if (!text.startsWith("'")) {
                break;
            }
            final var body = text.substring(1).trim();
            if (body.startsWith("ERROR:")) {
                message = body.substring("ERROR:".length()).trim();
            } else if (body.startsWith("LINE:")) {
                line = Integer.parseInt(body.substring("LINE:".length()).trim());
            } else if (body.startsWith("MAX-STEPS:")) {
                maxSteps = Long.parseLong(body.substring("MAX-STEPS:".length()).trim());
            } else if (body.startsWith("MAX-ARRAY:")) {
                maxArray = Integer.parseInt(body.substring("MAX-ARRAY:".length()).trim());
            } else if (!body.isEmpty()) {
                what.append(what.isEmpty() ? "" : " ").append(body);
            }
        }
        return new Expectation(outcome, what.toString(), message, line, maxSteps, maxArray);
    }
}
