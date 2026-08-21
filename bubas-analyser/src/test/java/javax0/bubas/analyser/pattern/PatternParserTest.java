package javax0.bubas.analyser.pattern;

import javax0.bubas.api.BubasDefinitionException;
import javax0.bubas.lexer.TokenType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class PatternParserTest {

    private static StatementPattern parse(String pattern) {
        return PatternParser.parse(pattern);
    }

    private static String rejection(String pattern) {
        return catchThrowableOfType(BubasDefinitionException.class,
                () -> PatternParser.parse(pattern)).getMessage();
    }

    private static Placeholder only(String pattern) {
        final var ps = parse(pattern).placeholders();
        assertThat(ps).as("expected exactly one placeholder").hasSize(1);
        return ps.getFirst();
    }

    @Nested
    @DisplayName("shape")
    class Shape {

        @Test
        void splits_literals_from_placeholders() {
            final var p = parse("VALIDATE {var:item} AGAINST {expression:rules}");
            assertThat(p.elements()).hasSize(4);
            assertThat(p.elements().get(0)).isEqualTo(new Literal(TokenType.WORD, "VALIDATE"));
            assertThat(p.elements().get(2)).isEqualTo(new Literal(TokenType.WORD, "AGAINST"));
            assertThat(p.placeholders()).extracting(Placeholder::name)
                    .containsExactly("item", "rules");
        }

        @Test
        void lexes_literals_the_way_source_is_lexed() {
            // The '[' and ']' sit against the braces with no spaces; they are still separate tokens.
            final var p = parse("SIZE OF {identifier/ARRAY:a}[{literal/INTEGER:i}] IS {var:n}");
            assertThat(p.elements()).filteredOn(Literal.class::isInstance)
                    .extracting(e -> ((Literal) e).text())
                    .containsExactly("SIZE", "OF", "[", "]", "IS");
        }

        @Test
        void an_unnamed_placeholder_takes_its_kind_as_its_name() {
            assertThat(only("EXECUTE {expression}").name()).isEqualTo("expression");
            assertThat(only("SHOW {new > identifier/INTEGER}").name()).isEqualTo("identifier");
        }

        @Test
        void reserves_every_word_literal_not_only_the_first() {
            assertThat(parse("PAY {expression:amount} VIA {var:account}").reservedWords())
                    .containsExactlyInAnyOrder("PAY", "VIA");
        }

        @Test
        void punctuation_literals_reserve_nothing() {
            assertThat(parse("SET {identifier/ARRAY:a}[{literal/INTEGER:i}]").reservedWords())
                    .containsExactly("SET");
        }
    }

    @Nested
    @DisplayName("conditions")
    class Conditions {

        @Test
        void reads_a_precondition_only_placeholder() {
            final var p = only("SHOW {initialized > var:x}");
            assertThat(p.preconditions()).containsExactly(Precondition.INITIALIZED);
            assertThat(p.postconditions()).isEmpty();
        }

        @Test
        void reads_a_postcondition_only_placeholder() {
            final var p = only("CLEAR {var:x > initialized}");
            assertThat(p.preconditions()).isEmpty();
            assertThat(p.postconditions()).containsExactly(Postcondition.INITIALIZED);
        }

        @Test
        void reads_both_axes_of_precondition() {
            final var p = only("ADD TO {mutable:initialized > var:total > initialized}");
            assertThat(p.preconditions())
                    .containsExactlyInAnyOrder(Precondition.MUTABLE, Precondition.INITIALIZED);
            assertThat(p.postconditions()).containsExactly(Postcondition.INITIALIZED);
        }

        @Test
        void spaces_around_the_separators_are_ignored() {
            assertThat(only("ADD TO {  mutable : initialized  >  var : total  >  initialized  }"))
                    .isEqualTo(only("ADD TO {mutable:initialized>var:total>initialized}"));
        }
    }

    @Nested
    @DisplayName("constraints")
    class Constraints {

        @Test
        void reads_a_plain_type_constraint() {
            assertThat(only("SHOW {var/INTEGER:count}").constraint())
                    .isEqualTo(new Constraint.Named("INTEGER", false));
        }

        @Test
        void reads_an_opaque_type_name_without_resolving_it() {
            assertThat(only("SHOW {var/Order:o}").constraint())
                    .isEqualTo(new Constraint.Named("Order", false));
        }

        @Test
        void reads_an_exact_reference() {
            assertThat(only("SWAP WITH {var/=a:b}").constraint())
                    .isEqualTo(new Constraint.Named("a", true));
        }

        @Test
        void reads_an_element_reference() {
            assertThat(only("SET {expression/a[]:value}").constraint())
                    .isEqualTo(new Constraint.ElementOf("a"));
        }

        @Test
        void reads_an_array_of_anything() {
            assertThat(only("SORT {var/ARRAY:a}").constraint())
                    .isEqualTo(new Constraint.ArrayOf(null));
        }

        @Test
        void reads_an_array_of_a_named_type() {
            assertThat(only("SORT {var/ARRAY/INTEGER:a}").constraint())
                    .isEqualTo(new Constraint.ArrayOf(new Constraint.Named("INTEGER", false)));
        }
    }

    @Nested
    @DisplayName("the built-in patterns")
    class BuiltIns {

        @Test
        void every_built_in_pattern_in_the_specification_parses() {
            final var builtIns = List.of(
                    "DECLARE {new > identifier/T:name > declared} {type:T}",
                    "DECLARE {new > identifier/T:name > initialized} {type:T} = {expression/T:init}",
                    "DECLARE {new > identifier/T:name > final} {type:T} FINAL = {expression/T:init}",
                    "DECLARE {new > identifier/ARRAY/T:name > initialized}"
                            + "[{expression/INTEGER:size}] {type:T}",
                    "{mutable:declared > var:name > initialized} = {expression/name:value}");
            for (final var pattern : builtIns) {
                assertThat(parse(pattern).source()).as("parsing %s", pattern).isEqualTo(pattern);
            }
        }

        @Test
        void a_forward_type_reference_is_accepted() {
            // '/T' refers to {type:T}, which appears later in the same pattern.
            final var p = parse("DECLARE {new > identifier/T:name > declared} {type:T}");
            assertThat(p.placeholders()).extracting(Placeholder::name).containsExactly("name", "T");
        }
    }

    @Nested
    @DisplayName("rejections")
    class Rejections {

        @Test
        void a_pattern_need_not_start_with_a_keyword() {
            // The built-in assignment begins with a placeholder and its only literal is '='.
            assertThat(parse("{var:x} IS SET").placeholders())
                    .extracting(Placeholder::name).containsExactly("x");
            assertThat(parse("{mutable:declared > var:name > initialized} = {expression/name:value}"))
                    .isNotNull();
        }

        @Test
        void a_pattern_of_placeholders_only_is_rejected() {
            assertThat(rejection("{var:a} {var:b}"))
                    .contains("reserves nothing")
                    .contains("at least one literal");
        }

        @Test
        void a_single_non_word_literal_is_enough() {
            assertThat(parse("{var:x} = {expression:v}").reservedWords()).isEmpty();
        }

        @Test
        void a_pattern_may_not_start_with_a_structural_keyword() {
            assertThat(rejection("IF {expression:c} MATCHES {var:p}"))
                    .contains("drives block parsing")
                    .contains("choose another word");
        }

        @Test
        void a_pattern_may_start_with_a_built_in_pattern_keyword() {
            assertThat(parse("DECLARE {new > identifier/T:n} {type:T} FROM {expression:src}")).isNotNull();
        }

        @Test
        void placeholder_names_must_be_unique() {
            assertThat(rejection("SWAP {var:a} WITH {var:a}")).contains("two placeholders are named 'a'");
        }

        @Test
        void an_unnamed_placeholder_collides_with_its_kind_spelling() {
            assertThat(rejection("EXECUTE {expression} TIMES {literal/INTEGER:expression}"))
                    .contains("two placeholders are named 'expression'");
        }

        @Test
        void a_state_word_may_not_be_a_name() {
            assertThat(rejection("SHOW {var:initialized}")).contains("is a state word");
        }

        @Test
        void an_unknown_kind_is_rejected() {
            assertThat(rejection("SHOW {thing:x}")).contains("names no kind");
        }

        @Test
        void an_unknown_condition_is_rejected() {
            assertThat(rejection("SHOW {frozen > var:x}")).contains("'frozen' is not a precondition");
        }

        @Test
        void two_preconditions_on_one_axis_are_rejected() {
            assertThat(rejection("SHOW {declared:initialized > var:x}"))
                    .contains("two assignment preconditions");
            assertThat(rejection("SHOW {mutable:final > var:x}"))
                    .contains("two mutability preconditions");
        }

        @Test
        void only_a_variable_placeholder_carries_conditions() {
            assertThat(rejection("CHECK {initialized > expression:rules}"))
                    .contains("only a var or identifier placeholder has conditions");
        }

        @Test
        void a_type_placeholder_takes_no_constraint() {
            assertThat(rejection("DECLARE {type/INTEGER:T}")).contains("takes no constraint");
        }

        @Test
        void a_new_placeholder_must_say_what_type_it_creates() {
            assertThat(rejection("FETCH INTO {new > identifier:out > initialized}"))
                    .contains("must carry a type constraint");
        }

        @Test
        void a_final_postcondition_also_requires_a_type() {
            assertThat(rejection("OPEN {identifier:handle > final}"))
                    .contains("must carry a type constraint");
        }

        @Test
        void a_final_postcondition_cannot_meet_an_existing_variable() {
            assertThat(rejection("OPEN {declared > identifier/Ledger:h > final}"))
                    .contains("implies the variable is new");
        }

        @Test
        void final_does_not_combine_with_another_postcondition() {
            assertThat(rejection("OPEN {new > identifier/Ledger:h > final:initialized}"))
                    .contains("combines 'final' with another postcondition");
        }

        @Test
        void a_var_cannot_be_created_because_it_may_be_indexed() {
            assertThat(rejection("FETCH INTO {new > var/Order:out > initialized}"))
                    .contains("may be an indexed reference")
                    .contains("use identifier");
            assertThat(rejection("OPEN {var/Ledger:h > final}")).contains("use identifier");
        }

        @Test
        void a_var_followed_by_a_literal_bracket_is_ambiguous() {
            assertThat(rejection("SET {var:a}[{expression/INTEGER:i}] = {expression:v}"))
                    .contains("nothing could say whether the hole takes 'a' or 'a[1]'");
        }

        @Test
        void an_identifier_may_be_followed_by_a_literal_bracket() {
            assertThat(parse("DECLARE {new > identifier/ARRAY/T:n > initialized}"
                    + "[{expression/INTEGER:size}] {type:T}")).isNotNull();
        }

        @Test
        void an_unclosed_brace_is_rejected() {
            assertThat(rejection("SHOW {var:x")).contains("never closed");
        }

        @Test
        void a_stray_closing_brace_is_rejected() {
            assertThat(rejection("SHOW var:x}")).contains("without a matching");
        }

        @Test
        void nested_braces_are_rejected() {
            assertThat(rejection("SHOW {var:{x}}")).contains("may not contain '{'");
        }

        @Test
        void an_empty_pattern_is_rejected() {
            assertThat(rejection("   ")).contains("must contain something");
        }

        @Test
        void unbalanced_brackets_in_a_pattern_are_rejected() {
            assertThat(rejection("SET {var:a}[{literal/INTEGER:i}")).contains("never closed");
        }

        @Test
        void the_message_always_names_the_pattern() {
            assertThat(rejection("SHOW {thing:x}")).startsWith("in pattern \"SHOW {thing:x}\":");
        }
    }
}
