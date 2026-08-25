package javax0.bubas.bunit.matchers;

import javax0.bubas.api.BubasType;
import javax0.bubas.api.Value;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the matchers mean, tested without a language.
 * <p>
 * A matcher is an ordinary object judging an ordinary value, so proving it needs no test program,
 * no interpreter and no vocabulary — which is also why this module can be used by a vocabulary that
 * shares none of ours. The end-to-end behaviour, where {@code ARGS} reaches a mock through real
 * statements, is tested where both halves are visible.
 */
@DisplayName("what a matcher means")
class MatcherTest {

    /** Values as an interpreter would hand them over, without borrowing one to make them. */
    private record Given(BubasType type, Object raw) implements Value {

        static Value integer(long value) {
            return new Given(BubasType.INTEGER, value);
        }

        static Value decimal(String value) {
            return new Given(BubasType.DECIMAL, new BigDecimal(value));
        }

        static Value text(String value) {
            return new Given(BubasType.STRING, value);
        }

        @Override
        public long asLong() {
            return (Long) raw;
        }

        @Override
        public BigDecimal asDecimal() {
            return (BigDecimal) raw;
        }

        @Override
        public String asString() {
            return (String) raw;
        }

        @Override
        public boolean asBoolean() {
            return (Boolean) raw;
        }

        @Override
        public <T> T as(Class<T> javaType) {
            return javaType.cast(raw);
        }
    }

    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final BigDecimal TEN = BigDecimal.TEN;

    @Nested
    @DisplayName("BETWEEN")
    class Range {

        private final javax0.bubas.bunit.Matcher matcher = new Between().call(null, ONE, TEN);

        @Test
        void includes_both_ends() {
            assertThat(matcher.matches(Given.integer(1))).isTrue();
            assertThat(matcher.matches(Given.integer(10))).isTrue();
        }

        @Test
        void excludes_what_lies_outside() {
            assertThat(matcher.matches(Given.integer(0))).isFalse();
            assertThat(matcher.matches(Given.integer(11))).isFalse();
        }

        @Test
        void takes_either_numeric_type() {
            assertThat(matcher.matches(Given.integer(5))).isTrue();
            assertThat(matcher.matches(Given.decimal("5.5"))).isTrue();
            assertThat(matcher.matches(Given.decimal("10.0"))).isTrue();
        }

        /** A matcher judges whatever it is handed: a non-number is a mismatch, not a failure. */
        @Test
        void a_value_that_is_not_a_number_simply_does_not_match() {
            assertThat(matcher.matches(Given.text("5"))).isFalse();
        }

        @Test
        void describes_itself_for_a_diagnostic() {
            assertThat(matcher.describe()).isEqualTo("between 1 and 10");
        }
    }

    @Nested
    @DisplayName("CONTAINS")
    class Substring {

        private final javax0.bubas.bunit.Matcher matcher = new Contains().call(null, "over limit");

        @Test
        void finds_the_text_anywhere_in_the_value() {
            assertThat(matcher.matches(Given.text("order is over limit, rejected"))).isTrue();
            assertThat(matcher.matches(Given.text("over limit"))).isTrue();
        }

        @Test
        void rejects_a_value_without_it() {
            assertThat(matcher.matches(Given.text("under the limit"))).isFalse();
        }

        @Test
        void a_value_that_is_not_a_string_simply_does_not_match() {
            assertThat(matcher.matches(Given.integer(3))).isFalse();
        }

        @Test
        void describes_itself_for_a_diagnostic() {
            assertThat(matcher.describe()).isEqualTo("containing \"over limit\"");
        }
    }

    @Nested
    @DisplayName("the numeric bounds")
    class Bounds {

        @Test
        void GREATER_THAN_excludes_the_bound_and_AT_LEAST_includes_it() {
            final var strict = new GreaterThan().call(null, TEN);
            final var inclusive = new AtLeast().call(null, TEN);
            assertThat(strict.matches(Given.integer(10))).isFalse();
            assertThat(inclusive.matches(Given.integer(10))).isTrue();
            assertThat(strict.matches(Given.integer(11))).isTrue();
            assertThat(inclusive.matches(Given.integer(9))).isFalse();
        }

        @Test
        void LESS_THAN_excludes_the_bound_and_AT_MOST_includes_it() {
            final var strict = new LessThan().call(null, TEN);
            final var inclusive = new AtMost().call(null, TEN);
            assertThat(strict.matches(Given.integer(10))).isFalse();
            assertThat(inclusive.matches(Given.integer(10))).isTrue();
            assertThat(strict.matches(Given.integer(9))).isTrue();
            assertThat(inclusive.matches(Given.integer(11))).isFalse();
        }

        @Test
        void a_bound_takes_either_numeric_type() {
            final var matcher = new GreaterThan().call(null, ONE);
            assertThat(matcher.matches(Given.decimal("1.5"))).isTrue();
            assertThat(matcher.matches(Given.decimal("0.5"))).isFalse();
        }

        @Test
        void a_value_that_is_not_a_number_simply_does_not_match() {
            assertThat(new AtLeast().call(null, ONE).matches(Given.text("5"))).isFalse();
        }

        @Test
        void each_describes_itself() {
            assertThat(new GreaterThan().call(null, TEN).describe()).isEqualTo("greater than 10");
            assertThat(new AtLeast().call(null, TEN).describe()).isEqualTo("at least 10");
            assertThat(new LessThan().call(null, TEN).describe()).isEqualTo("less than 10");
            assertThat(new AtMost().call(null, TEN).describe()).isEqualTo("at most 10");
        }
    }

    @Nested
    @DisplayName("ANYTHING and its inverse")
    class Wildcards {

        @Test
        void ANYTHING_takes_every_type() {
            final var matcher = new Anything().call(null);
            assertThat(matcher.matches(Given.integer(1))).isTrue();
            assertThat(matcher.matches(Given.text("x"))).isTrue();
            assertThat(matcher.matches(Given.decimal("0.0"))).isTrue();
            assertThat(matcher.describe()).isEqualTo("anything");
        }

        @Test
        void ANYTHING_BUT_inverts_what_it_wraps() {
            final var matcher = new AnythingBut().call(null, new Contains().call(null, "draft"));
            assertThat(matcher.matches(Given.text("final report"))).isTrue();
            assertThat(matcher.matches(Given.text("draft report"))).isFalse();
        }

        /** Inverting a type mismatch accepts it, which follows from a matcher judging rather than checking. */
        @Test
        void inverting_CONTAINS_accepts_a_value_that_is_not_a_string_at_all() {
            final var matcher = new AnythingBut().call(null, new Contains().call(null, "draft"));
            assertThat(matcher.matches(Given.integer(3))).isTrue();
        }

        @Test
        void it_describes_what_it_wraps() {
            assertThat(new AnythingBut().call(null, new Anything().call(null)).describe())
                    .isEqualTo("anything but anything");
        }
    }

    @Nested
    @DisplayName("the string matchers")
    class Strings {

        @Test
        void STARTS_WITH_and_ENDS_WITH_look_at_the_right_end() {
            final var starts = new StartsWith().call(null, "ERR-");
            final var ends = new EndsWith().call(null, "-EU");
            assertThat(starts.matches(Given.text("ERR-402-EU"))).isTrue();
            assertThat(starts.matches(Given.text("WARN-402-EU"))).isFalse();
            assertThat(ends.matches(Given.text("ERR-402-EU"))).isTrue();
            assertThat(ends.matches(Given.text("ERR-402-US"))).isFalse();
        }

        /** The whole value, not a part of it — CONTAINS is the matcher for a fragment. */
        @Test
        void MATCHES_fits_the_whole_value() {
            final var matcher = new Matches().call(null, "ORD-[0-9]+");
            assertThat(matcher.matches(Given.text("ORD-4711"))).isTrue();
            assertThat(matcher.matches(Given.text("see ORD-4711 please"))).isFalse();
        }

        @Test
        void a_value_that_is_not_a_string_simply_does_not_match() {
            assertThat(new StartsWith().call(null, "1").matches(Given.integer(12))).isFalse();
            assertThat(new EndsWith().call(null, "2").matches(Given.integer(12))).isFalse();
            assertThat(new Matches().call(null, "[0-9]+").matches(Given.integer(12))).isFalse();
        }

        @Test
        void a_malformed_expression_fails_while_the_matcher_is_built() {
            org.assertj.core.api.Assertions
                    .assertThatThrownBy(() -> new Matches().call(null, "ORD-[0-9"))
                    .isInstanceOf(java.util.regex.PatternSyntaxException.class);
        }

        @Test
        void each_describes_itself() {
            assertThat(new StartsWith().call(null, "a").describe()).isEqualTo("starting with \"a\"");
            assertThat(new EndsWith().call(null, "z").describe()).isEqualTo("ending with \"z\"");
            assertThat(new Matches().call(null, "[a-z]").describe()).isEqualTo("matching \"[a-z]\"");
        }
    }

    @Nested
    @DisplayName("ARGS")
    class Collecting {

        @Test
        void collects_its_arguments_in_order() {
            final var arguments = new Args().call(null, Given.integer(1), Given.text("two"));
            assertThat(arguments.values()).hasSize(2);
            assertThat(arguments.values().getFirst().asLong()).isEqualTo(1L);
            assertThat(arguments.values().getLast().asString()).isEqualTo("two");
        }

        @Test
        void collects_nothing_when_given_nothing() {
            assertThat(new Args().call(null).values()).isEmpty();
        }

        @Test
        void the_collected_list_cannot_be_changed_afterwards() {
            final var arguments = new Args().call(null, Given.integer(1));
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> arguments.values().clear())
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
