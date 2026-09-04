package javax0.bubas.bunit;

import javax0.bubas.analyser.BubasLanguage;
import javax0.bubas.api.Context;
import javax0.bubas.api.ExpressionArg;
import javax0.bubas.api.StatementContext;
import javax0.bubas.api.VariableArg;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The framework, driven by a vocabulary it has never heard of.
 * <p>
 * Every statement here is deliberately spelled unlike the standard ones — {@code FAKE} rather than
 * {@code IS MOCKED}, {@code PERFORM} rather than {@code RUN} — and none of the words appears
 * anywhere in {@code bubas-bunit}. If the framework recognised a statement by name instead of by
 * annotation, nothing in this file would work.
 * <p>
 * This is also the only place the repeatable {@link SuppliesVariable} is exercised: the standard
 * vocabulary supplies one variable per statement, so a statement supplying two is untested there.
 */
@DisplayName("a framework that does not know its own vocabulary")
class AlienVocabularyTest {

    // ---------------------------------------------------------------- the alien vocabulary

    @NamesTarget("target")
    @DeclaresMock
    public static final class Fake {
        static final String PATTERN = "FAKE {literal/STRING:target}";

        public void call(StatementContext ctx, String target) {
            ctx.service(MockRecorder.class).mockCommand(target);
        }
    }

    @NamesTarget("target")
    @DeclaresMock
    @SuppliesResult("answer")
    public static final class Gives {
        static final String PATTERN = "FAKE {literal/STRING:target} GIVES {expression:answer}";

        public void call(StatementContext ctx, String target, ExpressionArg answer) {
            ctx.service(MockRecorder.class).mockFunction(target, List.of(), answer.evaluate());
        }
    }

    /** One statement, two variables — which is what makes {@code @SuppliesVariable} repeatable. */
    @NamesTarget("target")
    @SuppliesVariable("one")
    @SuppliesVariable("two")
    public static final class FillBoth {
        // A comma, not AND: AND continues an expression, so the pattern parser rejects it as a
        // separator — the placeholder would swallow it.
        static final String PATTERN = "FILL {literal/STRING:target} WITH {literal/STRING:one}"
                + " = {expression:first}, {literal/STRING:two} = {expression:second}";

        public void call(StatementContext ctx, String target, String one, ExpressionArg first,
                         String two, ExpressionArg second) {
            final var recorder = ctx.service(MockRecorder.class);
            recorder.supplyVariable(target, one, first.evaluate());
            recorder.supplyVariable(target, two, second.evaluate());
        }
    }

    @NamesTarget("target")
    @SuppliesVariable("one")
    public static final class FillOne {
        static final String PATTERN = "FILL {literal/STRING:target} WITH {literal/STRING:one}"
                + " = {expression:first}";

        public void call(StatementContext ctx, String target, String one, ExpressionArg first) {
            ctx.service(MockRecorder.class).supplyVariable(target, one, first.evaluate());
        }
    }

    @Act
    public static final class Perform {
        static final String PATTERN = "PERFORM";

        public void call(StatementContext ctx) {
            ctx.service(MockRecorder.class).run();
        }
    }

    @Expectation
    public static final class Outcome {
        static final String PATTERN = "OUTCOME {expression:value}";

        public void call(StatementContext ctx, ExpressionArg value) {
            final var recorder = ctx.service(MockRecorder.class);
            final var expected = value.evaluate().asLong();
            final var actual = recorder.result().orElse(null);
            if (actual == null) {
                ctx.error("expected " + expected + " but the subject returned nothing");
            } else if (actual.asLong() != expected) {
                ctx.error("expected " + expected + " but was " + actual.asLong());
            }
        }
    }

    private static final BubasLanguage TESTS = BubasLanguage.builder()
            .defineStatement(Fake.PATTERN, Fake.class)
            .defineStatement(Gives.PATTERN, Gives.class)
            .defineStatement(FillBoth.PATTERN, FillBoth.class)
            .defineStatement(FillOne.PATTERN, FillOne.class)
            .defineStatement(Perform.PATTERN, Perform.class)
            .defineStatement(Outcome.PATTERN, Outcome.class)
            .defineFunction("MAYBE", Maybe.class)
            .seal();

    /**
     * A condition the compiler cannot see through. This language has no variables, so without it
     * there is no way to write a branch at all: a constant condition is now a compile error.
     */
    public static final class Maybe {
        public boolean call(Context ctx) {
            return true;
        }
    }

    // ---------------------------------------------------------------- the subject

    /** Writes two variables, neither opaque, so both have to be supplied by a mock. */
    public static final class Make {
        public void call(StatementContext ctx, VariableArg a, VariableArg b) {
            throw new IllegalStateException("the real implementation must never run under test");
        }
    }

    public static final class Count {
        public long call(Context ctx) {
            throw new IllegalStateException("the real implementation must never run under test");
        }
    }

    private static final BubasLanguage SUBJECT = BubasLanguage.builder()
            .defineStatement("MAKE {new > identifier/INTEGER:a > initialized}"
                    + " AND {new > identifier/INTEGER:b > initialized}", Make.class)
            .defineFunction("COUNT", Count.class)
            .seal();

    private static final String MAKES = """
            PROGRAM Making RETURNS INTEGER
                MAKE first AND second
                RETURN first + second
            END.
            """;

    private static final String COUNTS = """
            PROGRAM Counting RETURNS INTEGER
                RETURN COUNT()
            END.
            """;

    private static TestResult run(String subject, String test) {
        return BunitRunner.of(TESTS, SUBJECT, subject).run(test);
    }

    // ---------------------------------------------------------------- the tests

    @Test
    void a_vocabulary_the_framework_has_never_heard_of_works() {
        final var result = run(COUNTS, """
                PROGRAM CountingIsMocked
                    FAKE "COUNT" GIVES 7
                    PERFORM
                    OUTCOME 7
                END.
                """);
        assertThat(result.passed()).as("%s", result.diagnostic()).isTrue();
        assertThat(result.calls()).containsExactly("COUNT()");
    }

    @Test
    void a_failed_expectation_in_an_alien_vocabulary_still_names_its_line() {
        final var result = run(COUNTS, """
                PROGRAM WrongOutcome
                    FAKE "COUNT" GIVES 7
                    PERFORM
                    OUTCOME 8
                END.
                """);
        assertThat(result.passed()).isFalse();
        assertThat(result.diagnostic()).contains("expected 8 but was 7").contains("line 4");
    }

    @Test
    void one_statement_supplies_two_variables() {
        final var result = run(MAKES, """
                PROGRAM BothSupplied
                    FAKE "MAKE _ AND _"
                    FILL "MAKE _ AND _" WITH "a" = 3, "b" = 4
                    PERFORM
                    OUTCOME 7
                END.
                """);
        assertThat(result.passed()).as("%s", result.diagnostic()).isTrue();
    }

    @Test
    void supplying_only_one_of_two_is_refused_naming_the_other() {
        final var result = run(MAKES, """
                PROGRAM OnlyOneSupplied
                    FAKE "MAKE _ AND _"
                    FILL "MAKE _ AND _" WITH "a" = 3
                    PERFORM
                    OUTCOME 7
                END.
                """);
        assertThat(result.passed()).isFalse();
        assertThat(result.diagnostic())
                .contains("will not write 'b'")
                .contains("nothing supplies it on every path");
        assertThat(result.calls()).as("the subject never ran").isEmpty();
    }

    @Test
    void the_flow_analysis_applies_to_an_alien_vocabulary_too() {
        final var result = run(MAKES, """
                PROGRAM SuppliedOnOnePathOnly
                    FAKE "MAKE _ AND _"
                    IF MAYBE() THEN
                        FILL "MAKE _ AND _" WITH "a" = 3, "b" = 4
                    END IF
                    PERFORM
                    OUTCOME 7
                END.
                """);
        assertThat(result.passed()).isFalse();
        assertThat(result.diagnostic()).contains("nothing supplies it on every path");
    }

    @Test
    void an_expectation_before_the_act_is_refused_whatever_the_act_is_called() {
        final var result = run(COUNTS, """
                PROGRAM NoActYet
                    FAKE "COUNT" GIVES 7
                    OUTCOME 7
                    PERFORM
                END.
                """);
        assertThat(result.passed()).isFalse();
        assertThat(result.diagnostic()).contains("a run that has not happened yet");
    }

    @Test
    void a_test_with_no_act_at_all_is_refused() {
        final var result = run(COUNTS, """
                PROGRAM NeverRuns
                    FAKE "COUNT" GIVES 7
                END.
                """);
        assertThat(result.passed()).isFalse();
        assertThat(result.diagnostic()).contains("never runs the subject");
    }
}
