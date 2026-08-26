package javax0.bubas.analyser;

import javax0.bubas.api.BubasDefinitionException;
import javax0.bubas.api.BubasArray;
import javax0.bubas.api.BubasType;
import javax0.bubas.api.Surface;
import javax0.bubas.support.Standard;
import javax0.bubas.api.Context;
import javax0.bubas.api.ExpressionArg;
import javax0.bubas.api.Registrar;
import javax0.bubas.api.StatementContext;
import javax0.bubas.api.Value;
import javax0.bubas.api.VariableArg;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class BubasLanguageTest {

    public static final class Order {
    }

    public static final class Customer {
    }

    public static final class LoadOrder {
        public Order call(Context ctx, long orderId) {
            return new Order();
        }

        private static String unused() {
            return "helpers are private";
        }
    }

    public static final class OrderTotal {
        public BigDecimal call(Context ctx, Order order) {
            return BigDecimal.ONE;
        }
    }

    public static final class ValidateOrder {
        public boolean call(Context ctx, Order order) {
            return true;
        }
    }

    public static final class LogEvent {
        public void call(Context ctx, String level, String message) {
        }
    }

    public static final class Validate {
        public void call(StatementContext ctx, VariableArg item, ExpressionArg rules) {
        }
    }

    public static final class MadeByProvider {
        private MadeByProvider() {
        }

        public static MadeByProvider provider() {
            return new MadeByProvider();
        }

        public void call(StatementContext ctx, VariableArg item) {
        }
    }

    private static BubasLanguage.Builder base() {
        return BubasLanguage.builder()
                .defineOpaqueType("Order", Order.class)
                .defineFunction("LOAD_ORDER", LoadOrder.class);
    }

    private static String rejection(BubasLanguage.Builder builder) {
        return catchThrowableOfType(BubasDefinitionException.class, builder::seal).getMessage();
    }

    @Nested
    @DisplayName("naming a command")
    class Naming {

        public static final class Approve {
            public void call(StatementContext ctx, ExpressionArg target) {
            }
        }

        @javax0.bubas.api.BubasCommandName("LoanValidation")
        public static final class NamedValidate {
            public void call(StatementContext ctx, VariableArg item, ExpressionArg rules) {
            }
        }

        @javax0.bubas.api.BubasCommandName("loanvalidation")
        public static final class Lookalike {
            public void call(StatementContext ctx, ExpressionArg target) {
            }
        }

        @javax0.bubas.api.BubasCommandName("")
        public static final class Blank {
            public void call(StatementContext ctx, ExpressionArg target) {
            }
        }

        @javax0.bubas.api.BubasCommandName("Loan Validation")
        public static final class Spaced {
            public void call(StatementContext ctx, ExpressionArg target) {
            }
        }

        private static CommandDefinition only(BubasLanguage language, String keyword) {
            return language.commands().stream()
                    .filter(c -> c.pattern().keyword().filter(keyword::equals).isPresent())
                    .findFirst().orElseThrow();
        }

        @Test
        void an_unnamed_command_is_its_pattern_skeleton() {
            final var language = base()
                    .defineStatement("VALIDATE {initialized > var:item} AGAINST {expression:rules}",
                            Validate.class)
                    .seal();
            assertThat(only(language, "VALIDATE").name()).isEqualTo("VALIDATE _ AGAINST _");
            assertThat(only(language, "VALIDATE").isNamed()).isFalse();
        }

        @Test
        void punctuation_keeps_its_place_in_a_skeleton() {
            final var language = BubasLanguage.builder().install(Standard::register).seal();
            assertThat(language.commands()).extracting(CommandDefinition::name)
                    .contains("DECLARE _ _", "DECLARE _ _ = _", "DECLARE _ _ FINAL = _",
                            "DECLARE _[_] _", "_ = _");
        }

        @Test
        void an_annotation_replaces_the_skeleton() {
            final var language = base()
                    .defineStatement("VALIDATE {initialized > var:item} AGAINST {expression:rules}",
                            NamedValidate.class)
                    .seal();
            assertThat(only(language, "VALIDATE").name()).isEqualTo("LoanValidation");
            assertThat(only(language, "VALIDATE").isNamed()).isTrue();
        }

        @Test
        void two_named_commands_may_not_differ_only_in_case() {
            assertThat(rejection(base()
                    .defineStatement("VALIDATE {initialized > var:item} AGAINST {expression:rules}",
                            NamedValidate.class)
                    .defineStatement("CHECK {expression:target}", Lookalike.class)))
                    .contains("two commands are named 'LoanValidation' and 'loanvalidation'")
                    .contains("names are unique ignoring case");
        }

        @Test
        void a_blank_name_is_rejected() {
            assertThat(rejection(base().defineStatement("CHECK {expression:target}", Blank.class)))
                    .contains("@BubasCommandName is blank");
        }

        @Test
        void a_name_may_not_contain_whitespace() {
            assertThat(rejection(base().defineStatement("CHECK {expression:target}", Spaced.class)))
                    .contains("contains whitespace")
                    .contains("never be confused with a pattern skeleton");
        }

        /** Unnamed commands are not checked against each other: an app that never mocks pays nothing. */
        @Test
        void two_unnamed_commands_sharing_a_skeleton_are_not_rejected() {
            assertThatCode(() -> base()
                    .defineStatement("PAY {expression:x} VIA CARD", Approve.class)
                    .defineStatement("PAY {expression:x} FROM BANK", Approve.class)
                    .seal()).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("descriptions and the checksum that says they were reviewed")
    class Descriptions {

        /** A domain class that knows nothing about BUBAS, which is the point. */
        public static final class Parcel {
            public long weight() {
                return 0;
            }
        }

        @javax0.bubas.api.BubasDescribes(Parcel.class)
        @javax0.bubas.api.BubasDescription("Something posted to a customer.")
        public interface ParcelDoc {
        }

        @javax0.bubas.api.BubasDescribes(Parcel.class)
        @javax0.bubas.api.BubasDescription("Something posted to a customer.")
        @javax0.bubas.api.BubasReviewed("")
        public interface FirstTimeDoc {
        }

        @javax0.bubas.api.BubasDescribes(Parcel.class)
        @javax0.bubas.api.BubasDescription("Something posted to a customer.")
        @javax0.bubas.api.BubasReviewed("0000000000000000")
        public interface StaleDoc {
        }

        /**
         * The checksum below was not guessed: sealing with an empty one reported it, and it was
         * written here. That is the whole workflow, and this test is it being followed.
         */
        @javax0.bubas.api.BubasDescribes(Parcel.class)
        @javax0.bubas.api.BubasDescription("Something posted to a customer.")
        @javax0.bubas.api.BubasReviewed("6CC503F783713212")
        public interface ReviewedDoc {
        }

        public interface NotADescriptor {
        }

        @javax0.bubas.api.BubasReviewed("0000000000000000")
        public static final class StaleFunction {
            public long call(Context ctx) {
                return 0;
            }
        }

        private static String error(ThrowableAssert.ThrowingCallable building) {
            return catchThrowableOfType(BubasDefinitionException.class, building).getMessage();
        }

        @Test
        void a_descriptor_registers_the_class_it_describes() {
            final var language = BubasLanguage.builder()
                    .defineOpaqueTypeVia("Parcel", ParcelDoc.class).seal();
            assertThat(language.opaqueType("Parcel"))
                    .contains(BubasType.opaque("Parcel", Parcel.class));
        }

        @Test
        void an_interface_describing_nothing_is_refused() {
            assertThat(error(() -> BubasLanguage.builder()
                    .defineOpaqueTypeVia("Parcel", NotADescriptor.class)))
                    .contains("carries no @BubasDescribes")
                    .contains("Register the class itself with defineOpaqueType");
        }

        /** Reviewing is opt-in per class: no annotation, no check. */
        @Test
        void a_class_with_no_checksum_is_not_checked() {
            assertThat(BubasLanguage.builder()
                    .defineOpaqueTypeVia("Parcel", ParcelDoc.class).seal()).isNotNull();
        }

        /**
         * The first time there is nothing to compare against, so nobody is told to review
         * anything — only where to write the value.
         */
        @Test
        void an_empty_checksum_asks_only_that_the_value_be_written() {
            final var message = error(() -> BubasLanguage.builder()
                    .defineOpaqueTypeVia("Parcel", FirstTimeDoc.class).seal());
            assertThat(message)
                    .isEqualTo("write " + Surface.checksum(Parcel.class)
                            + " into @BubasReviewed on " + FirstTimeDoc.class.getTypeName())
                    .doesNotContain("Re-read")
                    .doesNotContain("has changed");
        }

        @Test
        void a_checksum_that_no_longer_matches_names_what_to_re_read() {
            final var message = error(() -> BubasLanguage.builder()
                    .defineOpaqueTypeVia("Parcel", StaleDoc.class).seal());
            assertThat(message)
                    .contains(Parcel.class.getTypeName() + " has changed since its description"
                            + " was reviewed")
                    .contains("Its public surface is now:")
                    .contains("long weight()")
                    .contains("Re-read the description, then write "
                            + Surface.checksum(Parcel.class))
                    .contains("on " + StaleDoc.class.getTypeName());
        }

        @Test
        void a_matching_checksum_seals() {
            assertThat(BubasLanguage.builder()
                    .defineOpaqueTypeVia("Parcel", ReviewedDoc.class).seal()
                    .opaqueTypes()).hasSize(1);
        }

        /**
         * If this fails because someone changed {@code Parcel}, the check is working: the message
         * names the new value to write into {@code ReviewedDoc}.
         */
        @Test
        void the_recorded_checksum_is_the_one_the_surface_yields() {
            assertThat(Surface.checksum(Parcel.class)).isEqualTo("6CC503F783713212");
        }

        /** A function's own class is its subject: there is no descriptor to stand in for it. */
        @Test
        void a_function_is_checked_against_its_own_surface() {
            assertThat(error(() -> BubasLanguage.builder()
                    .defineFunction("COUNT", StaleFunction.class).seal()))
                    .contains(StaleFunction.class.getTypeName() + " has changed")
                    .contains("long call(javax0.bubas.api.Context)");
        }
    }

    @Nested
    @DisplayName("defining and overriding")
    class Overriding {

        public static final class OtherLoad {
            public Order call(Context ctx, long id, String region) {
                return new Order();
            }
        }

        /** These fail while the language is being described, not at seal, so the whole chain runs here. */
        private static String error(ThrowableAssert.ThrowingCallable building) {
            return catchThrowableOfType(BubasDefinitionException.class, building).getMessage();
        }

        private static Map<String, Class<?>> map(String name, Class<?> implementation) {
            return Map.of(name, implementation);
        }

        @Test
        void defining_the_same_name_twice_is_an_error() {
            assertThat(error(() -> base().defineFunction("LOAD_ORDER", OtherLoad.class)))
                    .contains("function 'LOAD_ORDER' is already defined")
                    .contains("Say override() before it to replace it deliberately");
        }

        @Test
        void a_name_differing_only_in_case_is_the_same_name() {
            assertThat(error(() -> base().defineFunction("load_order", OtherLoad.class)))
                    .contains("function 'load_order' is already defined as 'LOAD_ORDER'");
        }

        @Test
        void an_opaque_type_defined_twice_is_an_error() {
            assertThat(error(() -> base().defineOpaqueType("Order", Customer.class)))
                    .contains("opaque type 'Order' is already defined");
        }

        @Test
        void a_statement_defined_twice_is_an_error() {
            final var pattern = "VALIDATE {initialized > var:item} AGAINST {expression:rules}";
            assertThat(error(() -> base().defineStatement(pattern, Validate.class)
                    .defineStatement(pattern, Validate.class)))
                    .contains("statement '" + pattern + "' is already defined");
        }

        @Test
        void override_replaces_what_is_there() {
            final var language = base().override()
                    .defineFunction("LOAD_ORDER", OtherLoad.class).seal();
            assertThat(language.function("LOAD_ORDER").orElseThrow().parameters()).hasSize(2);
            assertThat(language.functions()).hasSize(1);
        }

        @Test
        void override_uses_the_new_spelling_of_the_name() {
            final var language = base().override()
                    .defineFunction("load_order", OtherLoad.class).seal();
            assertThat(language.functions()).extracting(FunctionSignature::name)
                    .containsExactly("load_order");
        }

        /** An override of nothing is a rename nobody finished, or a typo. */
        @Test
        void overriding_something_that_is_not_there_is_an_error() {
            assertThat(error(() -> base().override().defineFunction("NO_SUCH", OtherLoad.class)))
                    .contains("there is no function 'NO_SUCH' to override");
        }

        @Test
        void the_flag_covers_exactly_one_definition() {
            assertThat(error(() -> base()
                    .override().defineFunction("LOAD_ORDER", OtherLoad.class)
                    .defineFunction("LOAD_ORDER", OtherLoad.class)))
                    .contains("is already defined");
        }

        @Test
        void override_does_not_extend_to_a_map() {
            assertThat(error(() -> base().override()
                    .defineFunctions(map("LOAD_ORDER", OtherLoad.class))))
                    .contains("override() is for one definition")
                    .contains("overrideAll()");
        }

        @Test
        void overrideAll_does_not_apply_to_a_single_definition() {
            assertThat(error(() -> base().overrideAll()
                    .defineFunction("LOAD_ORDER", OtherLoad.class)))
                    .contains("overrideAll() is for a map of definitions")
                    .contains("override()");
        }

        @Test
        void overrideAll_replaces_every_name_in_the_map() {
            final var language = base().overrideAll()
                    .defineFunctions(map("LOAD_ORDER", OtherLoad.class)).seal();
            assertThat(language.function("LOAD_ORDER").orElseThrow().parameters()).hasSize(2);
        }

        @Test
        void overrideAll_fails_when_any_name_is_absent() {
            assertThat(error(() -> base().overrideAll()
                    .defineFunctions(map("NO_SUCH", OtherLoad.class))))
                    .contains("there is no function 'NO_SUCH' to override");
        }

        /** A bundle decides its own definitions; an override has to name the one thing it replaces. */
        @Test
        void a_pending_override_may_not_be_carried_into_a_bundle() {
            assertThat(error(() -> base().override()
                    .install(registrar -> registrar.defineFunction("X", OtherLoad.class))))
                    .contains("install() follows override()");
        }

        @Test
        void two_flags_in_a_row_are_an_error() {
            assertThat(error(() -> base().override().override()))
                    .contains("override() follows override() with no definition between them");
        }

        @Test
        void a_flag_left_dangling_at_seal_is_an_error() {
            assertThat(rejection(base().override()))
                    .contains("override() was called but nothing was defined after it");
        }
    }

    @Nested
    @DisplayName("enumerating the vocabulary")
    class Enumeration {

        @Test
        void every_function_is_listed_in_registration_order() {
            final var language = base()
                    .defineFunction("ORDER_TOTAL", OrderTotal.class)
                    .defineFunction("LOG_EVENT", LogEvent.class)
                    .seal();
            assertThat(language.functions()).extracting(FunctionSignature::name)
                    .containsExactly("LOAD_ORDER", "ORDER_TOTAL", "LOG_EVENT");
        }

        @Test
        void every_opaque_type_is_listed_in_registration_order() {
            final var language = base().defineOpaqueType("Customer", Customer.class).seal();
            assertThat(language.opaqueTypes()).extracting(BubasType.Opaque::name)
                    .containsExactly("Order", "Customer");
        }

        @Test
        void a_listed_function_carries_its_whole_signature() {
            assertThat(base().seal().functions()).singleElement()
                    .hasToString("LOAD_ORDER(orderId INTEGER) -> Order");
        }

        @Test
        void a_language_with_no_functions_lists_none() {
            assertThat(BubasLanguage.builder().seal().functions()).isEmpty();
            assertThat(BubasLanguage.builder().seal().opaqueTypes()).isEmpty();
        }

        @Test
        void the_listings_are_immutable() {
            final var language = base().seal();
            assertThatThrownBy(() -> language.functions().clear())
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> language.opaqueTypes().clear())
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("the ANY wildcard parameter")
    class AnyParameter {

        public static final class Describe {
            public String call(Context ctx, Value value) {
                return value.type() + ":" + value.asString();
            }
        }

        public static final class ConcatAny {
            public String call(Context ctx, Value... parts) {
                return "";
            }
        }

        public static final class Mixed {
            public String call(Context ctx, String label, Value value) {
                return label;
            }
        }

        public static final class ReturnsAny {
            public Value call(Context ctx, long n) {
                return null;
            }
        }

        public static final class ReturnsAnyArray {
            public BubasArray call(Context ctx, long n) {
                return null;
            }
        }

        @Test
        void a_Value_parameter_reads_as_ANY() {
            assertThat(base().defineFunction("DESCRIBE", Describe.class).seal()
                    .function("DESCRIBE").orElseThrow())
                    .hasToString("DESCRIBE(value ANY) -> STRING");
        }

        @Test
        void ANY_combines_with_varargs() {
            assertThat(base().defineFunction("CONCAT", ConcatAny.class).seal()
                    .function("CONCAT").orElseThrow())
                    .hasToString("CONCAT(parts ANY...) -> STRING");
        }

        @Test
        void ANY_mixes_with_concrete_parameters() {
            assertThat(base().defineFunction("MIXED", Mixed.class).seal()
                    .function("MIXED").orElseThrow())
                    .hasToString("MIXED(label STRING, value ANY) -> STRING");
        }

        @Test
        void ANY_accepts_every_type_but_VOID() {
            assertThat(BubasType.ANY.accepts(BubasType.INTEGER)).isTrue();
            assertThat(BubasType.ANY.accepts(BubasType.STRING)).isTrue();
            assertThat(BubasType.ANY.accepts(BubasType.BOOLEAN)).isTrue();
            assertThat(BubasType.ANY.accepts(BubasType.arrayOf(BubasType.INTEGER))).isTrue();
            assertThat(BubasType.ANY.accepts(BubasType.opaque("Order", Order.class))).isTrue();
            assertThat(BubasType.ANY.accepts(BubasType.VOID)).isFalse();
        }

        @Test
        void nothing_accepts_ANY_so_it_cannot_spread() {
            assertThat(BubasType.STRING.accepts(BubasType.ANY)).isFalse();
            assertThat(BubasType.ANY_ARRAY.accepts(BubasType.ANY)).isFalse();
            assertThat(BubasType.arrayOf(BubasType.STRING).accepts(BubasType.ANY)).isFalse();
        }

        /** No untyped value may enter the script: a wildcard is a parameter and nothing else. */
        @Test
        void a_wildcard_return_type_is_rejected() {
            assertThat(rejection(base().defineFunction("BAD", ReturnsAny.class)))
                    .contains("returns ANY")
                    .contains("a wildcard may only be a parameter")
                    .contains("return the concrete type instead");
            assertThat(rejection(base().defineFunction("BAD", ReturnsAnyArray.class)))
                    .contains("returns ARRAY")
                    .contains("a wildcard may only be a parameter");
        }
    }

    @Nested
    @DisplayName("variadic functions")
    class Varargs {

        public static final class Join {
            public String call(Context ctx, String... parts) {
                return String.join("", parts);
            }
        }

        public static final class Labelled {
            public String call(Context ctx, String label, long... numbers) {
                return label + numbers.length;
            }
        }

        public static final class Gather {
            public void call(StatementContext ctx, ExpressionArg... arguments) {
            }
        }

        @Test
        void a_variadic_parameter_reports_its_element_type_not_the_array() {
            assertThat(base().defineFunction("JOIN", Join.class).seal()
                    .function("JOIN").orElseThrow())
                    .hasToString("JOIN(parts STRING...) -> STRING");
        }

        @Test
        void fixed_parameters_come_before_the_variadic_one() {
            final var signature = base().defineFunction("LABELLED", Labelled.class).seal()
                    .function("LABELLED").orElseThrow();
            assertThat(signature).hasToString("LABELLED(label STRING, numbers INTEGER...) -> STRING");
            assertThat(signature.required()).isEqualTo(1);
            assertThat(signature.varargs()).isTrue();
        }

        @Test
        void a_variadic_function_accepts_any_count_from_its_required_number_up() {
            final var signature = base().defineFunction("LABELLED", Labelled.class).seal()
                    .function("LABELLED").orElseThrow();
            assertThat(signature.accepts(0)).isFalse();
            assertThat(signature.accepts(1)).isTrue();
            assertThat(signature.accepts(7)).isTrue();
        }

        @Test
        void a_fixed_function_still_accepts_exactly_its_own_count() {
            final var signature = base().seal().function("LOAD_ORDER").orElseThrow();
            assertThat(signature.varargs()).isFalse();
            assertThat(signature.accepts(1)).isTrue();
            assertThat(signature.accepts(0)).isFalse();
            assertThat(signature.accepts(2)).isFalse();
        }

        /**
         * A command's parameters match its pattern's placeholders, which are fixed in number, so a
         * variadic handler could never be filled. Rejected at registration rather than mis-derived.
         */
        @Test
        void a_variadic_command_is_rejected() {
            assertThat(rejection(base().defineStatement("GATHER {expression:a}", Gather.class)))
                    .contains("is variadic")
                    .contains("fixed in number")
                    .contains("Only a function may be variadic");
        }
    }

    @Nested
    @DisplayName("bundles installed through a Registrar")
    class Bundles {

        /** A bundle: it sees a {@link Registrar}, never the builder. */
        private static void orderVocabulary(Registrar registrar) {
            registrar.defineOpaqueType("Customer", Customer.class)
                    .defineFunction("ORDER_TOTAL", OrderTotal.class)
                    .defineFunction("LOG_EVENT", LogEvent.class);
        }

        @Test
        void a_bundle_contributes_its_definitions() {
            final var language = base().install(Bundles::orderVocabulary).seal();
            assertThat(language.opaqueType("Customer"))
                    .contains(BubasType.opaque("Customer", Customer.class));
            assertThat(language.function("ORDER_TOTAL")).isPresent();
            assertThat(language.function("LOG_EVENT")).isPresent();
        }

        @Test
        void install_returns_the_builder_so_the_embedder_chain_continues() {
            final var language = base()
                    .install(Bundles::orderVocabulary)
                    .defineStatement("VALIDATE {initialized > var:item} AGAINST {expression:rules}",
                            Validate.class)
                    .seal();
            assertThat(language.commands()).hasSize(1);
            assertThat(language.function("ORDER_TOTAL")).isPresent();
        }

        @Test
        void a_bundle_may_install_another_bundle() {
            final var language = base()
                    .install(outer -> outer.install(Bundles::orderVocabulary)
                            .defineFunction("VALIDATE_ORDER", ValidateOrder.class))
                    .seal();
            assertThat(language.function("ORDER_TOTAL")).isPresent();
            assertThat(language.function("VALIDATE_ORDER")).isPresent();
        }

        /**
         * The point of the narrowing. A bundle must not be able to seal the language or switch
         * overlap analysis off on the embedder's behalf, and the only thing keeping those off the
         * interface is that nobody adds them — so assert their absence rather than trust it.
         */
        @Test
        void a_registrar_exposes_definition_methods_and_nothing_else() {
            assertThat(Registrar.class.getMethods())
                    .extracting(java.lang.reflect.Method::getName)
                    .containsExactlyInAnyOrder("defineOpaqueType", "defineFunction",
                            "defineStatement", "defineOpaqueTypes", "defineFunctions",
                            "defineStatements", "install");
        }
    }

    @Nested
    @DisplayName("sealing")
    class Sealing {

        @Test
        void a_language_builds_and_seals() {
            final var language = base()
                    .defineStatement("VALIDATE {initialized > var:item} AGAINST {expression:rules}",
                            Validate.class)
                    .seal();
            assertThat(language.opaqueType("Order")).contains(BubasType.opaque("Order", Order.class));
            assertThat(language.commands()).hasSize(1);
        }

        @Test
        void a_signature_is_derived_from_the_java_method() {
            final var signature = base().seal().function("LOAD_ORDER").orElseThrow();
            assertThat(signature.parameters()).singleElement()
                    .extracting(FunctionSignature.Parameter::type).isEqualTo(BubasType.INTEGER);
            assertThat(signature.returnType()).isEqualTo(BubasType.opaque("Order", Order.class));
            assertThat(signature).hasToString("LOAD_ORDER(orderId INTEGER) -> Order");
        }

        @Test
        void a_void_function_reads_without_an_arrow() {
            final var language = base().defineFunction("LOG_EVENT", LogEvent.class).seal();
            assertThat(language.function("LOG_EVENT").orElseThrow())
                    .hasToString("LOG_EVENT(level STRING, message STRING)");
        }

        @Test
        void an_opaque_parameter_resolves_to_its_registered_type() {
            final var language = base().defineFunction("ORDER_TOTAL", OrderTotal.class).seal();
            assertThat(language.function("ORDER_TOTAL").orElseThrow().parameters())
                    .singleElement().extracting(FunctionSignature.Parameter::type)
                    .isEqualTo(BubasType.opaque("Order", Order.class));
        }

        @Test
        void a_function_is_found_case_insensitively_and_the_vocabulary_reserves_it() {
            final var language = base().seal();
            assertThat(language.function("load_order")).isPresent();
            assertThat(language.vocabulary().isReserved("load_order")).isTrue();
            assertThat(language.vocabulary().isTypeName("ORDER")).isTrue();
        }

        @Test
        void a_provider_method_may_stand_in_for_a_constructor() {
            assertThatCode(() -> base()
                    .defineStatement("TOUCH {var:item}", MadeByProvider.class)
                    .seal()).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("implementation classes")
    class Implementations {

        public static final class Empty {
        }

        public static final class TwoMethods {
            public void call(Context ctx) {
            }

            public void also(Context ctx) {
            }
        }

        public static final class NoContext {
            public void call(long orderId) {
            }
        }

        public static final class WrongInteger {
            public void call(Context ctx, int orderId) {
            }
        }

        public static final class WithToString {
            public void call(Context ctx) {
            }

            @Override
            public String toString() {
                return "an Object override is not the implementation";
            }
        }

        @Test
        void a_class_with_no_public_method_is_rejected() {
            assertThat(rejection(base().defineFunction("F", Empty.class)))
                    .contains("declares no public instance method");
        }

        @Test
        void a_class_with_two_public_methods_is_rejected() {
            assertThat(rejection(base().defineFunction("F", TwoMethods.class)))
                    .contains("declares 2 public instance methods")
                    .contains("helpers must be private");
        }

        @Test
        void an_object_override_does_not_count_as_the_implementation() {
            assertThatCode(() -> base().defineFunction("F", WithToString.class).seal())
                    .doesNotThrowAnyException();
        }

        @Test
        void a_function_must_take_a_context_first() {
            assertThat(rejection(base().defineFunction("F", NoContext.class)))
                    .contains("must take a Context as its first parameter");
        }

        @Test
        void int_is_not_a_bubas_type_and_the_diagnostic_says_why() {
            assertThat(rejection(base().defineFunction("F", WrongInteger.class)))
                    .contains("is not a BUBAS type")
                    .contains("INTEGER is 64-bit, so use long");
        }
    }

    @Nested
    @DisplayName("commands")
    class Commands {

        public static final class WrongArity {
            public void call(StatementContext ctx, VariableArg only) {
            }
        }

        public static final class WrongKind {
            public void call(StatementContext ctx, ExpressionArg item, ExpressionArg rules) {
            }
        }

        public static final class FunctionShaped {
            public void call(Context ctx, VariableArg item) {
            }
        }

        private static final String VALIDATE =
                "VALIDATE {initialized > var:item} AGAINST {expression:rules}";

        @Test
        void a_command_must_take_a_statement_context_first() {
            assertThat(rejection(base().defineStatement("TOUCH {var:item}", FunctionShaped.class)))
                    .contains("must take a StatementContext as its first parameter");
        }

        @Test
        void the_parameter_count_must_match_the_placeholders() {
            assertThat(rejection(base().defineStatement(VALIDATE, WrongArity.class)))
                    .contains("the pattern has 2 placeholder(s)")
                    .contains("takes 1 beside the context");
        }

        @Test
        void each_placeholder_kind_fixes_its_parameter_type() {
            assertThat(rejection(base().defineStatement(VALIDATE, WrongKind.class)))
                    .contains("'item' is a var, so its parameter must be VariableArg");
        }
    }

    @Nested
    @DisplayName("name collisions")
    class Collisions {

        @Test
        void two_type_names_may_not_share_one_java_class() {
            assertThat(rejection(base().defineOpaqueType("Purchase", Order.class)))
                    .contains("both registered against")
                    .contains("must be one-to-one");
        }

        @Test
        void a_function_may_not_be_named_after_a_core_keyword() {
            assertThat(rejection(base().defineFunction("WHILE", LogEvent.class)))
                    .contains("collides with the core keyword WHILE");
        }

        @Test
        void a_function_may_not_collide_with_an_opaque_type() {
            assertThat(rejection(base().defineFunction("order", LogEvent.class)))
                    .contains("collides with opaque type 'Order'");
        }

        @Test
        void a_function_may_not_collide_with_a_pattern_keyword() {
            assertThat(rejection(base()
                    .defineStatement("VALIDATE {var:item}", MadeByProvider.class)
                    .defineFunction("validate", LogEvent.class)))
                    .contains("collides with a keyword of the pattern");
        }

        @Test
        void a_placeholder_may_not_be_named_after_a_type() {
            assertThat(rejection(base()
                    .defineStatement("TOUCH {var:Order}", MadeByProvider.class)))
                    .contains("is named after a type");
            assertThat(rejection(base()
                    .defineStatement("TOUCH {var:INTEGER}", MadeByProvider.class)))
                    .contains("is named after a type");
        }
    }

    @Nested
    @DisplayName("constraint resolution")
    class Constraints {

        public static final class OneLiteral {
            public void call(StatementContext ctx, long times) {
            }
        }

        public static final class OneValue {
            public void call(StatementContext ctx, javax0.bubas.api.Value v) {
            }
        }

        public static final class OneVar {
            public void call(StatementContext ctx, VariableArg v) {
            }
        }

        public static final class VarAndExpression {
            public void call(StatementContext ctx, VariableArg a, ExpressionArg e) {
            }
        }

        @Test
        void a_constraint_naming_nothing_is_rejected() {
            assertThat(rejection(base().defineStatement("TOUCH {var/Nonsense:x}", OneVar.class)))
                    .contains("'Nonsense' names no placeholder in this pattern, no built-in type, "
                            + "and no registered opaque type");
        }

        @Test
        void a_registered_opaque_type_resolves() {
            assertThatCode(() -> base()
                    .defineStatement("TOUCH {var/Order:x}", OneVar.class).seal())
                    .doesNotThrowAnyException();
        }

        @Test
        void a_reference_to_a_placeholder_in_the_same_pattern_resolves() {
            assertThatCode(() -> base()
                    .defineStatement("PUT {var:a} INTO {expression/a:e}", VarAndExpression.class)
                    .seal()).doesNotThrowAnyException();
        }

        @Test
        void an_element_reference_needs_the_target_to_be_an_array() {
            assertThat(rejection(base()
                    .defineStatement("PUT {var:a} INTO {expression/a[]:e}", VarAndExpression.class)))
                    .contains("which is not declared to be an array; constrain it with /ARRAY");
            assertThatCode(() -> base()
                    .defineStatement("PUT {var/ARRAY:a} INTO {expression/a[]:e}",
                            VarAndExpression.class).seal()).doesNotThrowAnyException();
        }

        @Test
        void an_element_reference_to_a_missing_placeholder_is_rejected() {
            assertThat(rejection(base()
                    .defineStatement("PUT {var:a} INTO {expression/z[]:e}", VarAndExpression.class)))
                    .contains("refers to a placeholder named 'z', which this pattern does not have");
        }

        @Test
        void an_exact_match_against_NUMBER_makes_no_sense() {
            assertThat(rejection(base()
                    .defineStatement("SCALE BY {literal/=NUMBER:n}", OneValue.class)))
                    .contains("not a type but a choice between INTEGER and DECIMAL");
        }

        @Test
        void a_constrained_literal_fixes_its_java_parameter_type() {
            assertThatCode(() -> base()
                    .defineStatement("RETRY {literal/INTEGER:times}", OneLiteral.class).seal())
                    .doesNotThrowAnyException();
            assertThat(rejection(base()
                    .defineStatement("RETRY {literal/INTEGER:times}", OneValue.class)))
                    .contains("must be long, not Value");
        }

        @Test
        void an_unconstrained_literal_arrives_as_a_value() {
            assertThatCode(() -> base()
                    .defineStatement("RETRY {literal:times}", OneValue.class).seal())
                    .doesNotThrowAnyException();
        }

        @Test
        void a_literal_is_never_opaque_and_never_an_array() {
            assertThat(rejection(base()
                    .defineStatement("RETRY {literal/Order:n}", OneValue.class)))
                    .contains("a constant is never opaque");
            assertThat(rejection(base()
                    .defineStatement("RETRY {literal/ARRAY:n}", OneValue.class)))
                    .contains("a constant is never an array");
        }
    }

    @Nested
    @DisplayName("overlap analysis at seal")
    class Overlap {

        public static final class Two {
            public void call(StatementContext ctx, VariableArg target, ExpressionArg value) {
            }
        }

        public static final class TwoVars {
            public void call(StatementContext ctx, VariableArg target, VariableArg source) {
            }
        }

        @Test
        void colliding_patterns_are_rejected_when_the_language_seals() {
            assertThat(rejection(base()
                    .defineStatement("SET {var:target} TO {expression:value}", Two.class)
                    .defineStatement("SET {var:target} TO {var:source}", TwoVars.class)))
                    .contains("could match the same line");
        }

        @Test
        void the_check_can_be_skipped() {
            assertThatCode(() -> base()
                    .defineStatement("SET {var:target} TO {expression:value}", Two.class)
                    .defineStatement("SET {var:target} TO {var:source}", TwoVars.class)
                    .skipOverlapAnalysis(true)
                    .seal()).doesNotThrowAnyException();
        }
    }
}
