package javax0.bubas.analyser.core;

import javax0.bubas.analyser.BubasLanguage;
import javax0.bubas.api.BubasAssigns;
import javax0.bubas.api.BubasDefinitionException;
import javax0.bubas.api.BubasException;
import javax0.bubas.api.BubasStatic;
import javax0.bubas.api.Context;
import javax0.bubas.api.ExpressionArg;
import javax0.bubas.api.StatementContext;
import javax0.bubas.api.VariableArg;
import javax0.bubas.support.Standard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * What the analysis learns about a variable, and what it refuses to assume.
 * <p>
 * The rejections themselves are in the script corpus. This is about the boundary: which commands
 * teach it a value, and which leave it knowing nothing.
 */
class DeadCodeTest {

    /** Hands a variable over and says nothing about what it does with it. */
    public static final class Scramble {
        public void call(StatementContext ctx, VariableArg value) {
            value.set(0L);
        }
    }

    /** Says it copies, so the compiler may believe the value. */
    @BubasAssigns(target = "into", value = "from")
    public static final class Copy {
        public void call(StatementContext ctx, ExpressionArg from, VariableArg into) {
            into.set(from.evaluate());
        }
    }

    /** Writes two variables from two sources, and says so about both. */
    @BubasAssigns(target = "low", value = "from")
    @BubasAssigns(target = "high", value = "to")
    public static final class Span {
        public void call(StatementContext ctx, ExpressionArg from, ExpressionArg to,
                         VariableArg low, VariableArg high) {
            low.set(from.evaluate());
            high.set(to.evaluate());
        }
    }

    /** Writes two, and admits it knows what happens to only one of them. */
    @BubasAssigns(target = "kept", value = "from")
    public static final class Halve {
        public void call(StatementContext ctx, ExpressionArg from, VariableArg kept,
                         VariableArg rest) {
            kept.set(from.evaluate());
            rest.set(0L);
        }
    }

    /** Answers from its argument alone, and says so. */
    @BubasStatic
    public static final class Doubled {
        public long call(Context ctx, long value) {
            return value * 2;
        }
    }

    /** The same arithmetic, saying nothing. The compiler may not call it. */
    public static final class Quiet {
        public long call(Context ctx, long value) {
            return value * 2;
        }
    }

    /** Declares itself static and then asks for something a compiler has not got. */
    @BubasStatic
    public static final class Fibbing {
        public long call(Context ctx, long value) {
            return ctx.service(java.time.Clock.class) == null ? value : 0;
        }
    }

    /** Static, and entitled to refuse. */
    @BubasStatic
    public static final class Positive {
        public long call(Context ctx, long value) {
            if (value <= 0) {
                ctx.error(value + " is not positive");
            }
            return value;
        }
    }

    private static final BubasLanguage LANGUAGE = BubasLanguage.builder()
            .install(Standard::register)
            .defineStatement("SCRAMBLE {initialized > var/INTEGER:value > initialized}",
                    Scramble.class)
            .defineStatement("COPY {expression/INTEGER:from}"
                    + " INTO {initialized > var/INTEGER:into > initialized}", Copy.class)
            .defineStatement("SHOW {initialized > var:value}", Scramble.class)
            .defineStatement("SPAN {expression/INTEGER:from} THROUGH {expression/INTEGER:to}"
                    + " INTO {initialized > var/INTEGER:low > initialized}"
                    + " AND {initialized > var/INTEGER:high > initialized}", Span.class)
            .defineStatement("HALVE {expression/INTEGER:from}"
                    + " INTO {initialized > var/INTEGER:kept > initialized}"
                    + " DROPPING {initialized > var/INTEGER:rest > initialized}", Halve.class)
            .defineFunction("DOUBLED", Doubled.class)
            .defineFunction("QUIET", Quiet.class)
            .defineFunction("FIBBING", Fibbing.class)
            .defineFunction("POSITIVE", Positive.class)
            .seal();

    /** The rejection message, or null when it compiles. */
    private static String reject(String body) {
        final var thrown = catchThrowableOfType(BubasException.class,
                () -> LANGUAGE.compile("PROGRAM P\n" + body + "\nEND."));
        return thrown == null ? null : thrown.getMessage();
    }

    @Nested
    @DisplayName("a value is known only where the command said so")
    class Learning {

        @Test
        void a_command_that_says_nothing_clouds_what_it_touches() {
            assertThat(reject("""
                    DECLARE n INTEGER
                    n = 5
                    SCRAMBLE n
                    IF n > 10 THEN
                        n = 1
                    END IF
                    SCRAMBLE n"""))
                    .as("SCRAMBLE may have written anything, so the condition is a real question")
                    .isNull();
        }

        @Test
        void a_command_that_declares_what_it_copies_is_believed() {
            assertThat(reject("""
                    DECLARE n INTEGER
                    n = 0
                    COPY 5 INTO n
                    IF n > 10 THEN
                        n = 1
                    END IF
                    SCRAMBLE n"""))
                    .isEqualTo("this condition is always FALSE, so this arm cannot run; delete it");
        }

        @Test
        void what_it_copies_may_itself_be_unknown() {
            assertThat(reject("""
                    DECLARE n INTEGER
                    DECLARE m INTEGER
                    m = 0
                    SCRAMBLE m
                    n = 0
                    COPY m INTO n
                    IF n > 10 THEN
                        n = 1
                    END IF
                    SHOW n"""))
                    .as("the copied value was not known, so neither is the target")
                    .isNull();
        }
    }

    @Nested
    @DisplayName("a function is called while compiling only if it said it may be")
    class Static {

        @Test
        void a_declared_function_is_called() {
            assertThat(reject("""
                    DECLARE n INTEGER
                    n = DOUBLED(3)
                    IF n > 10 THEN
                        n = 1
                    END IF
                    SHOW n"""))
                    .isEqualTo("this condition is always FALSE, so this arm cannot run; delete it");
        }

        @Test
        void an_undeclared_one_is_opaque_however_pure_it_looks() {
            assertThat(reject("""
                    DECLARE n INTEGER
                    n = QUIET(3)
                    IF n > 10 THEN
                        n = 1
                    END IF
                    SHOW n"""))
                    .as("purity cannot be inferred, only declared")
                    .isNull();
        }

        @Test
        void an_unknown_argument_stops_it() {
            assertThat(reject("""
                    DECLARE n INTEGER
                    n = 0
                    SCRAMBLE n
                    n = DOUBLED(n)
                    IF n > 10 THEN
                        n = 1
                    END IF
                    SHOW n"""))
                    .isNull();
        }

        @Test
        void a_static_function_may_refuse_and_that_is_a_compile_error() {
            assertThat(reject("""
                    DECLARE n INTEGER
                    n = POSITIVE(-1)
                    SHOW n"""))
                    .isEqualTo("POSITIVE: -1 is not positive");
        }

        @Test
        void one_that_asks_for_a_service_gives_itself_away() {
            assertThat(reject("""
                    DECLARE n INTEGER
                    n = FIBBING(1)
                    SHOW n"""))
                    .contains("declared @BubasStatic but asked for Clock");
        }
    }

    @Nested
    @DisplayName("one statement may settle several variables")
    class Several {

        @Test
        void both_targets_are_learned() {
            assertThat(reject("""
                    DECLARE low INTEGER
                    DECLARE high INTEGER
                    low = 0
                    high = 0
                    SPAN 1 THROUGH 9 INTO low AND high
                    IF high > 20 THEN
                        low = 1
                    END IF
                    SHOW low"""))
                    .isEqualTo("this condition is always FALSE, so this arm cannot run; delete it");
        }

        @Test
        void the_one_it_kept_quiet_about_stays_unknown() {
            assertThat(reject("""
                    DECLARE kept INTEGER
                    DECLARE rest INTEGER
                    kept = 0
                    rest = 0
                    HALVE 8 INTO kept DROPPING rest
                    IF rest > 20 THEN
                        kept = 1
                    END IF
                    SHOW kept"""))
                    .as("nothing was declared about 'rest', so its value is not known")
                    .isNull();
        }

        @Test
        void the_one_it_declared_is_still_learned() {
            assertThat(reject("""
                    DECLARE kept INTEGER
                    DECLARE rest INTEGER
                    kept = 0
                    rest = 0
                    HALVE 8 INTO kept DROPPING rest
                    IF kept > 20 THEN
                        kept = 1
                    END IF
                    SHOW rest"""))
                    .isEqualTo("this condition is always FALSE, so this arm cannot run; delete it");
        }
    }

    @Nested
    @DisplayName("a claim about placeholders is checked when the language is sealed")
    class Declaration {

        private String seal(Class<?> implementation) {
            final var thrown = catchThrowableOfType(BubasDefinitionException.class,
                    () -> BubasLanguage.builder()
                            .defineStatement("PUT {expression/INTEGER:from}"
                                    + " IN {initialized > var/INTEGER:into > initialized}",
                                    implementation)
                            .seal());
            return thrown == null ? null : thrown.getMessage();
        }

        @BubasAssigns(target = "nowhere", value = "from")
        public static final class UnknownTarget {
            public void call(StatementContext ctx, ExpressionArg from, VariableArg into) {
            }
        }

        @BubasAssigns(target = "into", value = "nothing")
        public static final class UnknownValue {
            public void call(StatementContext ctx, ExpressionArg from, VariableArg into) {
            }
        }

        @BubasAssigns(target = "from", value = "into")
        public static final class BackToFront {
            public void call(StatementContext ctx, ExpressionArg from, VariableArg into) {
            }
        }

        @BubasAssigns(target = "into", value = "from")
        @BubasAssigns(target = "into", value = "from")
        public static final class SaidTwice {
            public void call(StatementContext ctx, ExpressionArg from, VariableArg into) {
            }
        }

        @Test
        void a_target_may_not_be_claimed_twice() {
            assertThat(seal(SaidTwice.class)).contains("as its target twice");
        }

        @Test
        void the_target_has_to_exist() {
            assertThat(seal(UnknownTarget.class)).contains("names 'nowhere' as its target",
                    "no such placeholder");
        }

        @Test
        void the_value_has_to_exist() {
            assertThat(seal(UnknownValue.class)).contains("names 'nothing' as its value");
        }

        @Test
        void only_a_variable_can_be_assigned() {
            assertThat(seal(BackToFront.class)).contains("only a variable can be assigned");
        }
    }
}
