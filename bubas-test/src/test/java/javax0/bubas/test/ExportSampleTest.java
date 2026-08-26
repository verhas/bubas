package javax0.bubas.test;

import javax0.bubas.export.VocabularyExport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Writes the whole vocabulary of {@link Environment} into {@code target}, in both forms.
 * <p>
 * Assertions tell you a thing is true; they do not let you look at it. Anyone deciding whether an
 * export is worth generating — or wondering what an LLM would actually be handed — wants the
 * artefact itself, and a paragraph describing it is not the same as reading one.
 * <p>
 * A test rather than a script, so it cannot rot: the files are rewritten by every build, and the
 * build fails if the language stops being exportable.
 */
@DisplayName("a sample export, written where it can be read")
class ExportSampleTest {

    private static final Path OUTPUT = Path.of("target");

    @Test
    void the_whole_vocabulary_is_written_in_both_forms() throws IOException {
        final var export = VocabularyExport.of(Environment.language());

        final var markdown = OUTPUT.resolve("vocabulary.md");
        final var json = OUTPUT.resolve("vocabulary.json");
        Files.createDirectories(OUTPUT);
        Files.writeString(markdown, export.asMarkdown(), StandardCharsets.UTF_8);
        Files.writeString(json, export.asJson(), StandardCharsets.UTF_8);

        System.out.println("vocabulary written to " + markdown.toAbsolutePath()
                + "\n                  and " + json.toAbsolutePath());

        assertThat(markdown).isNotEmptyFile();
        assertThat(json).isNotEmptyFile();
    }

    /**
     * The standard statements are in it. A generator told only about the embedder's own vocabulary
     * would know how to load an order and not how to hold one.
     */
    @Test
    void the_export_covers_the_standard_vocabulary_as_well_as_the_embedder_s() {
        final var export = VocabularyExport.of(Environment.language());

        assertThat(export.commands()).extracting(VocabularyExport.Command::name)
                .contains("DECLARE _ _", "_ = _", "ASSERT _, _");
        assertThat(export.functions()).extracting(VocabularyExport.Function::name)
                .contains("TO_INTEGER", "LENGTH", "WRAP", "SHOW");
        assertThat(export.types()).extracting(VocabularyExport.Type::name).contains("Parcel");
    }

    @Test
    void every_entry_carries_a_description() {
        final var export = VocabularyExport.of(Environment.language());
        assertThat(export.types()).allSatisfy(type ->
                assertThat(type.description()).isNotBlank());
        assertThat(export.functions()).allSatisfy(function ->
                assertThat(function.description()).isNotBlank());
        assertThat(export.commands()).allSatisfy(command ->
                assertThat(command.description()).isNotBlank());
    }
}
