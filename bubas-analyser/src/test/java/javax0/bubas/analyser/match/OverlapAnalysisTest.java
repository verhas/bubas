package javax0.bubas.analyser.match;

import javax0.bubas.analyser.pattern.PatternParser;
import javax0.bubas.analyser.pattern.StatementPattern;
import javax0.bubas.api.BubasDefinitionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class OverlapAnalysisTest {

    /**
     * Builds the vocabulary from the patterns under test, exactly as seal() does. It has to be all
     * of them: whether two patterns collide can depend on a word a third pattern reserves.
     */
    private static OverlapAnalysis analysisOf(List<StatementPattern> patterns) {
        return new OverlapAnalysis(Vocabulary.builder()
                .function("LOAD_ORDER", "COUNT_ORDERS")
                .opaqueType("Order")
                .patterns(patterns)
                .build());
    }

    private static List<StatementPattern> parse(String... patterns) {
        return Arrays.stream(patterns).map(PatternParser::parse).toList();
    }

    private static boolean collide(String left, String right) {
        final var patterns = parse(left, right);
        return analysisOf(patterns).overlap(patterns.getFirst(), patterns.getLast());
    }

    @Nested
    @DisplayName("patterns that collide")
    class Collide {

        @Test
        void a_variable_is_also_an_expression() {
            assertThat(collide("SET {var:target} TO {expression:value}",
                    "SET {var:target} TO {var:source}")).isTrue();
        }

        @Test
        void a_constant_is_also_an_expression() {
            assertThat(collide("RETRY {literal/INTEGER:n}", "RETRY {expression:e}")).isTrue();
        }

        @Test
        void a_pattern_collides_with_itself() {
            assertThat(collide("PING {var:x}", "PING {var:x}")).isTrue();
        }

        @Test
        void an_expression_can_swallow_a_shorter_pattern_tail() {
            // 'SEND a' matches the first; 'SEND a b c' only the second — but 'SEND a' fits both.
            assertThat(collide("SEND {var:x}", "SEND {expression:e}")).isTrue();
        }

        @Test
        void a_var_with_no_index_is_just_a_name() {
            // 'SHOW total' fits both; the var's index is optional, so its shortest form is a name.
            assertThat(collide("SHOW {var:x}", "SHOW {identifier:n}")).isTrue();
        }
    }

    @Nested
    @DisplayName("patterns that do not collide")
    class Distinct {

        @Test
        void different_keywords_never_collide() {
            assertThat(collide("PAY {expression:a} VIA {var:b}",
                    "SEND {expression:a} VIA {var:b}")).isFalse();
        }

        @Test
        void an_expression_stops_at_a_word_the_other_pattern_reserves() {
            // Only true because VIA and FROM are reserved by these very patterns, which is why
            // this analysis cannot run before every pattern is registered.
            assertThat(collide("PAY {expression:a} VIA {var:b}",
                    "PAY {expression:a} FROM {var:b}")).isFalse();
        }

        @Test
        void a_name_is_never_a_type_name() {
            assertThat(collide("TAKE {identifier:n}", "TAKE {type:t}")).isFalse();
        }

        @Test
        void a_constant_is_never_a_type_name() {
            assertThat(collide("TAG {literal/STRING:s}", "TAG {type:t}")).isFalse();
        }

        @Test
        void differing_lengths_do_not_collide() {
            assertThat(collide("OPEN {identifier:a} {type:t}", "OPEN {identifier:a}")).isFalse();
        }
    }

    @Nested
    @DisplayName("the built-in patterns")
    class BuiltIns {

        private static final List<StatementPattern> BUILT_INS = parse(
                "DECLARE {new > identifier/T:name > declared} {type:T}",
                "DECLARE {new > identifier/T:name > initialized} {type:T} = {expression/T:init}",
                "DECLARE {new > identifier/T:name > final} {type:T} FINAL = {expression/T:init}",
                "DECLARE {new > identifier/ARRAY/T:name > initialized}"
                        + "[{expression/INTEGER:size}] {type:T}",
                "{mutable:declared > var:name > initialized} = {expression/name:value}");

        @Test
        void no_two_built_ins_can_match_the_same_line() {
            assertThatCode(() -> analysisOf(BUILT_INS).check(BUILT_INS)).doesNotThrowAnyException();
        }

        @Test
        void a_custom_pattern_may_join_them() {
            final var all = new java.util.ArrayList<>(BUILT_INS);
            all.addAll(parse("VALIDATE {initialized > var:item} AGAINST {expression:rules}",
                    "PAY {expression:amount} VIA {var:account}"));
            assertThatCode(() -> analysisOf(all).check(all)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("reporting")
    class Reporting {

        @Test
        void a_conflict_names_both_patterns() {
            final var patterns = parse("SET {var:target} TO {expression:value}",
                    "SET {var:target} TO {var:source}");
            final var e = catchThrowableOfType(BubasDefinitionException.class,
                    () -> analysisOf(patterns).check(patterns));
            assertThat(e.getMessage())
                    .startsWith("these two patterns could match the same line:")
                    .contains("SET {var:target} TO {expression:value}")
                    .contains("SET {var:target} TO {var:source}");
        }

        @Test
        void every_conflicting_pair_is_reported_at_once() {
            final var patterns = parse("SET {var:t} TO {expression:v}",
                    "SET {var:t} TO {var:s}",
                    "RETRY {literal/INTEGER:n}",
                    "RETRY {expression:e}");
            final var e = catchThrowableOfType(BubasDefinitionException.class,
                    () -> analysisOf(patterns).check(patterns));
            assertThat(e.getMessage()).startsWith("2 pairs of patterns could match the same line:");
        }

        @Test
        void a_clean_vocabulary_passes_silently() {
            final var patterns = parse("PAY {expression:a} VIA {var:b}", "SEND {var:x}");
            assertThatCode(() -> analysisOf(patterns).check(patterns)).doesNotThrowAnyException();
        }
    }
}
