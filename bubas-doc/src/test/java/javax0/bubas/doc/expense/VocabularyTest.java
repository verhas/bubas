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

    private static final Map<String, BubasLanguage> STAGES = Map.of(
            "1", Expense.STAGE_1,
            "2", Expense.STAGE_2,
            "3", Expense.STAGE_3,
            "4", Expense.STAGE_4,
            "5", Expense.STAGE_5);

    @Test
    void every_stage_can_describe_itself() throws IOException {
        for (final var stage : STAGES.entrySet()) {
            // snippet: export
            final var export = VocabularyExport.of(stage.getValue());
            final var document = export.asMarkdown();
            // end snippet
            Runs.write("vocabulary-stage-" + stage.getKey() + ".md", document);
        }
    }

    /** Each stage adds and none takes away, which is the promise the whole book rests on. */
    @Test
    void each_stage_contains_everything_the_one_before_it_had() {
        final var order = List.of(Expense.STAGE_1, Expense.STAGE_2, Expense.STAGE_3,
                Expense.STAGE_4, Expense.STAGE_5);

        for (int i = 1; i < order.size(); i++) {
            final var earlier = VocabularyExport.of(order.get(i - 1));
            final var later = VocabularyExport.of(order.get(i));

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
        final var order = List.of(Expense.STAGE_1, Expense.STAGE_2, Expense.STAGE_3,
                Expense.STAGE_4, Expense.STAGE_5);

        for (int i = 1; i < order.size(); i++) {
            final var before = names(VocabularyExport.of(order.get(i - 1)));
            final var after = VocabularyExport.of(order.get(i));
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
    @Test
    void an_undescribed_vocabulary_cannot_be_exported() throws IOException {
        final var undescribed = javax0.bubas.analyser.BubasLanguage.builder()
                .install(javax0.bubas.support.Standard::register)
                .defineOpaqueType("Report", Expense.Report.class)
                .defineFunction("TOTAL_OF", Expense.TotalOf.class)
                .seal();

        final var thrown = org.assertj.core.api.Assertions.catchThrowable(
                () -> VocabularyExport.of(undescribed));

        assertThat(thrown).isNotNull();
        Runs.write("export-refusal.txt", thrown.getMessage());
    }
}
