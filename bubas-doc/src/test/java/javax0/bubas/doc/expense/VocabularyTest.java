package javax0.bubas.doc.expense;

import javax0.bubas.analyser.BubasLanguage;
import javax0.bubas.export.VocabularyExport;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Writes the vocabulary of every stage as a document, which is what the chapters show instead of
 * Java.
 * <p>
 * Parts 1 and 2 of the book contain no Java at all — see {@code DOCUMENTATION/AUTHORING.md} D16 —
 * so when a chapter says the language has gained an operation, it shows the reader the same
 * description the language itself would hand a subject-matter expert. Generated from the sealed
 * language object, so it cannot describe operations the language does not have.
 */
class VocabularyTest {

    /** Every stage, in order. The deltas between them are what the chapters show. */
    private static final List<BubasLanguage> ORDER = List.of(
            Expense.STAGE_1, Expense.STAGE_2, Expense.STAGE_3, Expense.STAGE_4, Expense.STAGE_5,
            Expense.STAGE_6, Expense.STAGE_7, Expense.STAGE_8, Expense.STAGE_9);

    @Test
    void every_stage_can_describe_itself() throws IOException {
        for (int i = 0; i < ORDER.size(); i++) {
            final var language = ORDER.get(i);
            // snippet: export
            final var export = VocabularyExport.of(language);
            final var document = export.asMarkdown();
            // end snippet
            Runs.write("vocabulary-stage-" + (i + 1) + ".md", document);
        }
    }

    /** Each stage adds and none takes away, which is the promise the whole book rests on. */
    @Test
    void each_stage_contains_everything_the_one_before_it_had() {
        for (int i = 1; i < ORDER.size(); i++) {
            final var earlier = VocabularyExport.of(ORDER.get(i - 1));
            final var later = VocabularyExport.of(ORDER.get(i));

            assertThat(later.functions()).extracting(VocabularyExport.Function::name)
                    .describedAs("stage %d keeps every function of stage %d", i + 1, i)
                    .containsAll(earlier.functions().stream()
                            .map(VocabularyExport.Function::name).toList());
            assertThat(later.commands()).extracting(VocabularyExport.Command::name)
                    .containsAll(earlier.commands().stream()
                            .map(VocabularyExport.Command::name).toList());
            assertThat(later.types()).extracting(VocabularyExport.Type::name)
                    .containsAll(earlier.types().stream()
                            .map(VocabularyExport.Type::name).toList());
        }
    }

    /**
     * What each stage adds to the one before it, rendered as the reader sees it.
     * <p>
     * A chapter that says "the vocabulary gains these operations" shows this file, so the claim is
     * generated from the two language objects rather than typed from memory. Sections are lifted
     * whole out of the full export, so the wording is the export's own.
     */
    @Test
    void what_each_stage_adds_is_written_out() throws IOException {
        for (int i = 1; i < ORDER.size(); i++) {
            final var before = names(VocabularyExport.of(ORDER.get(i - 1)));
            final var after = VocabularyExport.of(ORDER.get(i));
            final var added = new java.util.LinkedHashSet<>(names(after));
            added.removeAll(before);

            assertThat(added)
                    .describedAs("stage %d must add something, or it is not a stage", i + 1)
                    .isNotEmpty();

            Runs.write("vocabulary-added-" + (i + 1) + ".md", sections(after.asMarkdown(), added));
        }
    }

    private static List<String> names(VocabularyExport export) {
        return Stream.of(
                        export.types().stream().map(VocabularyExport.Type::name),
                        export.functions().stream().map(VocabularyExport.Function::name),
                        export.commands().stream().map(VocabularyExport.Command::name))
                .flatMap(s -> s).toList();
    }

    /** Every {@code ###} section of the export whose heading names one of these. */
    private static String sections(String markdown, java.util.Set<String> wanted) {
        final var out = new StringBuilder();
        String heading = null;
        final var block = new StringBuilder();

        for (final var line : (markdown + "\n## end").split("\n")) {
            final var isHeading = line.startsWith("## ") || line.startsWith("### ");
            if (!isHeading) {
                if (heading != null) {
                    block.append(line).append('\n');
                }
                continue;
            }
            final var closing = heading;
            if (closing != null && wanted.stream().anyMatch(
                    name -> closing.startsWith("### " + name + "(")
                            || closing.equals("### " + name))) {
                out.append(closing).append('\n')
                        .append(block.toString().stripTrailing()).append("\n\n");
            }
            heading = line.startsWith("### ") ? line : null;
            block.setLength(0);
        }
        return out.toString();
    }

    /**
     * Chapter 26: the export refuses to be built out of things nobody has described, and lists
     * every one of them. Captured rather than quoted, because the wording is the product.
     */
    /** A class nobody described, and no interface stands in for it either. */
    public static final class Ledger {
    }

    @javax0.bubas.api.BubasDescription("What a ledger holds.")
    public static final class BalanceOf {
        public java.math.BigDecimal call(javax0.bubas.api.Context ctx, Ledger ledger) {
            return java.math.BigDecimal.ZERO;
        }
    }

    @Test
    void an_undescribed_vocabulary_cannot_be_exported() throws IOException {
        final var undescribed = javax0.bubas.analyser.BubasLanguage.builder()
                .install(javax0.bubas.support.Standard::register)
                .defineOpaqueType("Ledger", Ledger.class)
                .defineFunction("BALANCE_OF", BalanceOf.class)
                .seal();

        final var thrown = org.assertj.core.api.Assertions.catchThrowable(
                () -> VocabularyExport.of(undescribed));

        assertThat(thrown).isNotNull();
        Runs.write("export-refusal.txt", thrown.getMessage());
    }

    // snippet: reviewed-first-time
    /** A type whose description has never been reviewed. The empty value asks for a checksum. */
    @javax0.bubas.api.BubasDescription("A cost centre a claim is charged to.")
    @javax0.bubas.api.BubasReviewed("")
    public static final class Budget {
        public String code() {
            return "CC-1";
        }
    }
    // end snippet

    @javax0.bubas.api.BubasDescription("What a budget has left.")
    public static final class LeftIn {
        public java.math.BigDecimal call(javax0.bubas.api.Context ctx, Budget budget) {
            return java.math.BigDecimal.ZERO;
        }
    }

    /** The same type, with a checksum somebody wrote down before its shape moved. */
    @javax0.bubas.api.BubasDescription("A cost centre a claim is charged to.")
    @javax0.bubas.api.BubasReviewed("0000000000000000")
    public static final class Stale {
        public String code() {
            return "CC-1";
        }
    }

    @javax0.bubas.api.BubasDescription("What a budget has left.")
    public static final class LeftInStale {
        public java.math.BigDecimal call(javax0.bubas.api.Context ctx, Stale budget) {
            return java.math.BigDecimal.ZERO;
        }
    }

    private static javax0.bubas.analyser.BubasLanguage over(String name, Class<?> type,
                                                            Class<?> function) {
        return javax0.bubas.analyser.BubasLanguage.builder()
                .install(javax0.bubas.support.Standard::register)
                .defineOpaqueType(name, type)
                .defineFunction("LEFT_IN", function)
                .seal();
    }

    /**
     * Chapter 26: what a review checksum says the first time, and what it says once a described
     * class has changed shape. Both captured, because the wording is the product.
     */
    @Test
    void a_review_checksum_reports_rather_than_writes() throws IOException {
        final var first = org.assertj.core.api.Assertions.catchThrowable(
                () -> VocabularyExport.of(over("Budget", Budget.class, LeftIn.class)));
        final var moved = org.assertj.core.api.Assertions.catchThrowable(
                () -> VocabularyExport.of(over("Budget", Stale.class, LeftInStale.class)));

        assertThat(first).hasMessageStartingWith("write ");
        assertThat(moved).hasMessageContaining("has changed since its description was reviewed");

        Runs.write("reviewed-first-time.txt", first.getMessage());
        Runs.write("reviewed-changed.txt", moved.getMessage());
    }
}
