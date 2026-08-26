package javax0.bubas.export;

import javax0.bubas.analyser.BubasLanguage;
import javax0.bubas.api.BubasDefinitionException;
import javax0.bubas.api.BubasDescribes;
import javax0.bubas.api.BubasDescription;
import javax0.bubas.api.Context;
import javax0.bubas.api.ExpressionArg;
import javax0.bubas.api.StatementContext;
import javax0.bubas.api.Value;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

@DisplayName("describing a sealed language")
class VocabularyExportTest {

    /** A domain class that knows nothing about BUBAS. */
    public static final class Order {
    }

    @BubasDescribes(Order.class)
    @BubasDescription("""
            An order a customer placed, as the order service knows it.
            Ask about one with ORDER_TOTAL; there is no way to look inside it.
            """)
    public interface OrderDoc {
    }

    @BubasDescription("Finds an order by the identifier the customer was given. Fails if none.")
    public static final class LoadOrder {
        public Order call(Context ctx, long orderId) {
            return new Order();
        }
    }

    @BubasDescription("What the customer will be charged, before tax.")
    public static final class OrderTotal {
        public BigDecimal call(Context ctx, Order order) {
            return BigDecimal.ONE;
        }
    }

    @BubasDescription("Joins any number of values into one line of text.")
    public static final class Join {
        public String call(Context ctx, Value... parts) {
            return "";
        }
    }

    @BubasDescription("Sends the order onward for fulfilment. Nothing further happens to it here.")
    public static final class Approve {
        public void call(StatementContext ctx, ExpressionArg target) {
        }
    }

    @BubasDescription("Counts the orders of a region, leaving the number in the variable named.")
    public static final class CountInto {
        public void call(StatementContext ctx, javax0.bubas.api.VariableArg total,
                         ExpressionArg region) {
        }
    }

    @BubasDescription("Says something, and answers nothing.")
    public static final class Shout {
        public void call(Context ctx, String text) {
        }
    }

    @BubasDescription("A description with a \"quoted\" word,\nand a line break in it.")
    public static final class Awkward {
        public long call(Context ctx) {
            return 0;
        }
    }

    public static final class Undescribed {
        public long call(Context ctx) {
            return 0;
        }
    }

    private static BubasLanguage.Builder described() {
        return BubasLanguage.builder()
                .defineOpaqueTypeVia("Order", OrderDoc.class)
                .defineFunction("LOAD_ORDER", LoadOrder.class)
                .defineFunction("ORDER_TOTAL", OrderTotal.class)
                .defineFunction("JOIN", Join.class)
                .defineStatement("APPROVE {expression/Order:target}", Approve.class)
                .defineStatement("COUNT ORDERS INTO {new > identifier/INTEGER:total > initialized}"
                        + " FOR {expression/STRING:region}", CountInto.class);
    }

    @Test
    void a_type_is_described_by_the_interface_standing_in_for_it() {
        assertThat(VocabularyExport.of(described().seal()).types()).singleElement()
                .satisfies(type -> {
                    assertThat(type.name()).isEqualTo("Order");
                    assertThat(type.description()).startsWith("An order a customer placed");
                });
    }

    @Test
    void a_function_carries_its_shape_and_its_meaning() {
        assertThat(VocabularyExport.of(described().seal()).functions())
                .filteredOn(function -> function.name().equals("LOAD_ORDER"))
                .singleElement()
                .satisfies(function -> {
                    assertThat(function.parameters()).singleElement()
                            .satisfies(parameter -> {
                                assertThat(parameter.name()).isEqualTo("orderId");
                                assertThat(parameter.type()).isEqualTo("INTEGER");
                            });
                    assertThat(function.returns()).isEqualTo("Order");
                    assertThat(function.variadic()).isFalse();
                    assertThat(function.description()).contains("Fails if none");
                });
    }

    @Test
    void a_variadic_function_says_so() {
        assertThat(VocabularyExport.of(described().seal()).functions())
                .filteredOn(function -> function.name().equals("JOIN"))
                .singleElement()
                .satisfies(function -> assertThat(function.variadic()).isTrue());
    }

    @Test
    void a_command_carries_its_syntax_and_which_slots_it_writes() {
        assertThat(VocabularyExport.of(described().seal()).commands())
                .filteredOn(command -> command.name().startsWith("COUNT"))
                .singleElement()
                .satisfies(command -> {
                    assertThat(command.name()).isEqualTo("COUNT ORDERS INTO _ FOR _");
                    assertThat(command.syntax()).contains("initialized");
                    assertThat(command.slots()).extracting(VocabularyExport.Slot::name)
                            .containsExactly("total", "region");
                    assertThat(command.slots().getFirst().written()).isTrue();
                    assertThat(command.slots().getFirst().type()).isEqualTo("INTEGER");
                    assertThat(command.slots().getLast().written()).isFalse();
                });
    }

    /** An export with holes in it reads like documentation, which is worse than having none. */
    @Test
    void everything_undescribed_is_named_at_once() {
        final var language = described().defineFunction("TALLY", Undescribed.class).seal();
        assertThat(catchThrowableOfType(BubasDefinitionException.class,
                () -> VocabularyExport.of(language)).getMessage())
                .contains("nothing describes:")
                .contains("function TALLY")
                .contains("Add @BubasDescription to each");
    }

    @Test
    void an_undescribed_opaque_type_is_named_too() {
        final var language = BubasLanguage.builder()
                .defineOpaqueType("Order", Order.class).seal();
        assertThat(catchThrowableOfType(BubasDefinitionException.class,
                () -> VocabularyExport.of(language)).getMessage())
                .contains("opaque type Order");
    }

    /** A language without descriptions seals, compiles and runs; it only cannot be exported. */
    @Test
    void a_language_without_descriptions_is_otherwise_perfectly_well() {
        assertThat(BubasLanguage.builder().defineFunction("TALLY", Undescribed.class).seal()
                .functions()).hasSize(1);
    }

    @Test
    void the_markdown_reads_as_a_vocabulary_rather_than_a_dump() {
        final var markdown = VocabularyExport.of(described().seal()).asMarkdown();
        assertThat(markdown)
                .contains("### LOAD_ORDER(orderId INTEGER) -> Order")
                .contains("### JOIN(parts ANY...) -> STRING")
                .contains("### COUNT ORDERS INTO _ FOR _")
                .contains("COUNT ORDERS INTO {new > identifier/INTEGER:total > initialized}")
                .contains("Leaves a value in: total");
        assertThat(markdown.indexOf("## Values"))
                .as("values first: every function and command is about one of them")
                .isLessThan(markdown.indexOf("## Functions"));
        assertThat(markdown.indexOf("## Functions")).isLessThan(markdown.indexOf("## Statements"));
    }

    @Test
    void a_void_function_reads_without_an_arrow() {
        final var language = BubasLanguage.builder()
                .defineFunction("SHOUT", Shout.class).seal();
        assertThat(VocabularyExport.of(language).asMarkdown()).contains("### SHOUT(text STRING)\n");
    }

    @Test
    void the_json_carries_the_shape_a_tool_needs() {
        assertThat(VocabularyExport.of(described().seal()).asJson())
                .contains("\"name\": \"LOAD_ORDER\"")
                .contains("{\"name\": \"orderId\", \"type\": \"INTEGER\"}")
                .contains("\"variadic\": true")
                .contains("{\"name\": \"total\", \"kind\": \"identifier\", \"type\": \"INTEGER\","
                        + " \"written\": true}");
    }

    /** A newline in a description would otherwise close the string and produce nonsense. */
    @Test
    void the_json_escapes_what_a_description_may_contain() {
        final var language = BubasLanguage.builder()
                .defineFunction("AWKWARD", Awkward.class).seal();
        final var json = VocabularyExport.of(language).asJson();
        assertThat(json)
                .contains("\\n")
                .contains("a \\\"quoted\\\" word")
                .doesNotContain("a \"quoted\" word");
    }

    @Test
    void no_java_appears_in_the_export() {
        final var json = VocabularyExport.of(described().seal()).asJson();
        final var markdown = VocabularyExport.of(described().seal()).asMarkdown();
        assertThat(json).doesNotContain("javax0").doesNotContain("class").doesNotContain("Java");
        assertThat(markdown).doesNotContain("javax0").doesNotContain(".class");
    }
}
