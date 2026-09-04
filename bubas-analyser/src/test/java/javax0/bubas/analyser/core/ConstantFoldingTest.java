package javax0.bubas.analyser.core;

import javax0.bubas.analyser.BubasLanguage;
import javax0.bubas.api.BubasException;
import javax0.bubas.api.StatementContext;
import javax0.bubas.api.VariableArg;
import javax0.bubas.support.Standard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * That the values agree is the script corpus's job. This is about the tree: whether the operation
 * is still there afterwards, which is the only way to tell folding from a coincidence.
 */
class ConstantFoldingTest {

    /** Reads a variable so nothing trips the unused-variable check; its argument is a slot,
     * never an expression, so it adds nothing to what {@link #expressions} finds. */
    public static final class Show {
        public void call(StatementContext ctx, VariableArg value) {
        }
    }

    private static final BubasLanguage LANGUAGE = BubasLanguage.builder()
            .install(Standard::register)
            .defineStatement("SHOW {initialized > var:value}", Show.class)
            .seal();

    private static CoreProgram compiled(String body) {
        return LANGUAGE.compile("PROGRAM P\n" + body + "\nEND.").core();
    }

    /** The rejection message, or null when it compiles. */
    private static String reject(String body) {
        final var thrown = catchThrowableOfType(BubasException.class, () -> compiled(body));
        return thrown == null ? null : thrown.getMessage();
    }

    /** Every expression in the program, in no particular order. */
    private static List<CoreExpression> expressions(CoreProgram program) {
        final var found = new ArrayList<CoreExpression>();
        walk(program.body(), found);
        return found;
    }

    private static void walk(List<CoreStatement> body, List<CoreExpression> found) {
        for (final var statement : body) {
            switch (statement) {
                case CoreStatement.Invoke invoke -> invoke.arguments().values().forEach(argument -> {
                    if (argument instanceof CoreArgument.Lazy lazy) {
                        descend(lazy.expression(), found);
                    }
                });
                case CoreStatement.Branch branch -> {
                    branch.arms().forEach(arm -> {
                        descend(arm.condition(), found);
                        walk(arm.body(), found);
                    });
                    if (branch.otherwise() != null) {
                        walk(branch.otherwise(), found);
                    }
                }
                case CoreStatement.Loop loop -> {
                    descend(loop.condition(), found);
                    walk(loop.body(), found);
                }
                case CoreStatement.Count count -> {
                    descend(count.from(), found);
                    descend(count.to(), found);
                    walk(count.body(), found);
                }
                default -> {
                }
            }
        }
    }

    private static void descend(CoreExpression expression, List<CoreExpression> found) {
        found.add(expression);
        switch (expression) {
            case CoreExpression.Arithmetic a -> {
                descend(a.left(), found);
                descend(a.right(), found);
            }
            case CoreExpression.Concat c -> {
                descend(c.left(), found);
                descend(c.right(), found);
            }
            case CoreExpression.Compare c -> {
                descend(c.left(), found);
                descend(c.right(), found);
            }
            case CoreExpression.Logical l -> {
                descend(l.left(), found);
                descend(l.right(), found);
            }
            case CoreExpression.Not n -> descend(n.operand(), found);
            case CoreExpression.Negate n -> descend(n.operand(), found);
            case CoreExpression.Widen w -> descend(w.operand(), found);
            case CoreExpression.Text t -> descend(t.operand(), found);
            default -> {
                }
        }
    }

    private static Object onlyConstant(String body) {
        final var all = expressions(compiled(body));
        assertThat(all).as("%s", all).hasSize(1);
        assertThat(all.get(0)).isInstanceOf(CoreExpression.Constant.class);
        return ((CoreExpression.Constant) all.get(0)).value();
    }

    @Nested
    @DisplayName("the operation is gone, not merely correct")
    class Folded {

        @Test
        void integer_arithmetic_collapses_to_one_node() {
            assertThat(onlyConstant("DECLARE n INTEGER\nn = 2 + 3 * 4\nSHOW n"))
                    .isEqualTo(14L);
        }

        @Test
        void decimal_addition_collapses_and_keeps_scale() {
            assertThat(onlyConstant("DECLARE d DECIMAL\nd = 0.10 + 0.20\nSHOW d"))
                    .isEqualTo(new BigDecimal("0.30"));
        }

        @Test
        void a_widened_literal_collapses() {
            assertThat(onlyConstant("DECLARE d DECIMAL\nd = 1.5 * 2\nSHOW d"))
                    .isEqualTo(new BigDecimal("3.0"));
        }

        @Test
        void concatenation_and_text_conversion_collapse() {
            assertThat(onlyConstant("DECLARE s STRING\ns = \"\" + 42 + \"!\"\nSHOW s"))
                    .isEqualTo("42!");
        }

        @Test
        void comparison_collapses() {
            assertThat(onlyConstant("DECLARE b BOOLEAN\nb = 2 < 3\nSHOW b")).isEqualTo(true);
        }

        @Test
        void a_variable_stops_it() {
            final var all = expressions(compiled("DECLARE n INTEGER\nn = 1\nn = n + 2\nSHOW n"));
            assertThat(all).as("no constant propagation: 'n + 2' survives")
                    .anyMatch(CoreExpression.Arithmetic.class::isInstance);
        }
    }

    @Nested
    @DisplayName("division reads the language's rounding policy")
    class Division {

        private String quotient(int digits) {
            final var language = BubasLanguage.builder()
                    .install(Standard::register)
                    .defineStatement("SHOW {initialized > var:value}", Show.class)
                    .mathContext(new MathContext(digits, RoundingMode.HALF_EVEN))
                    .seal();
            final var core = language.compile("""
                    PROGRAM P
                        DECLARE d DECIMAL
                        d = 1.0 / 3.0
                        SHOW d
                    END.""").core();
            final var all = expressions(core);
            assertThat(all).as("the division was folded").hasSize(1);
            return ((CoreExpression.Constant) all.get(0)).value().toString();
        }

        @Test
        void the_folded_quotient_depends_on_the_language() {
            assertThat(quotient(5)).isEqualTo("0.33333");
            assertThat(quotient(10)).isEqualTo("0.3333333333");
        }
    }

    @Nested
    @DisplayName("a constant that cannot be computed is a compile error")
    class Traps {

        @Test
        void division_by_zero() {
            assertThat(reject("DECLARE n INTEGER\nn = 1 / 0\nSHOW n"))
                    .isEqualTo("division by zero");
        }

        @Test
        void mod_by_zero() {
            assertThat(reject("DECLARE n INTEGER\nn = 1 MOD 0\nSHOW n"))
                    .isEqualTo("MOD by zero");
        }

        @Test
        void overflow() {
            assertThat(reject("DECLARE n INTEGER\nn = 9223372036854775807 + 1\nSHOW n"))
                    .isEqualTo("integer overflow");
        }

        @Test
        void it_does_not_matter_that_the_line_could_never_run() {
            assertThat(reject("""
                    DECLARE n INTEGER
                    DECLARE go BOOLEAN
                    n = 0
                    go = FALSE
                    IF go THEN
                        n = 1 / 0
                    END IF
                    SHOW n"""))
                    .isEqualTo("division by zero");
        }
    }
}
