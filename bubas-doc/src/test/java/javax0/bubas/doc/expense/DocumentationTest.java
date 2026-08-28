package javax0.bubas.doc.expense;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the documents themselves, not the code they quote.
 * <p>
 * When an {@code INCLUDE} marker stops matching — a snippet renamed, a line reworded — mdship does
 * not fail. It reports success and writes an <em>empty</em> block, so the document silently loses
 * the very thing it was written to show. The staleness gate in CI catches the moment that happens,
 * because the file changes; it cannot catch an empty block that was already committed, because
 * from then on there is nothing to diff.
 * <p>
 * This is the state check that pairs with it. It found the five-minute tutorial's language
 * definition sitting empty after the vocabulary was restructured. See
 * {@code DOCUMENTATION/AUTHORING.md} D5.
 */
class DocumentationTest {

    private static final Path ROOT = Path.of("..");

    /** An INCLUDE opening marker, its generated body, and its terminator. */
    private static final Pattern INCLUDE =
            Pattern.compile("<!--INCLUDE(.*?)-->\\n(.*?)<!--/INCLUDE-->", Pattern.DOTALL);

    private static final Pattern FROM = Pattern.compile("from:\\s*\"([^\"]+)\"");

    private static List<Path> documents() throws IOException {
        try (var walk = Files.walk(ROOT.resolve("DOCUMENTATION"))) {
            return Stream.concat(
                            walk.filter(Files::isRegularFile),
                            Stream.of(ROOT.resolve("README.md"), ROOT.resolve("SPEC.md")))
                    .filter(p -> p.getFileName().toString().endsWith(".md"))
                    .toList();
        }
    }

    @Test
    void no_document_contains_an_empty_generated_block() throws IOException {
        final var empty = new ArrayList<String>();

        for (final var document : documents()) {
            final var matcher = INCLUDE.matcher(Files.readString(document));
            while (matcher.find()) {
                final var body = matcher.group(2).replaceAll("(?m)^```.*$", "").strip();
                if (body.isEmpty()) {
                    final var from = FROM.matcher(matcher.group(1));
                    empty.add(document.normalize() + "  <-  "
                            + (from.find() ? from.group(1) : "(no from:)"));
                }
            }
        }

        assertThat(empty)
                .describedAs("INCLUDE blocks that generated nothing — the marker no longer matches")
                .isEmpty();
    }

    /** Cheap corollary: a document that includes nothing at all has probably lost its markers. */
    @Test
    void the_documents_that_should_quote_code_still_do() throws IOException {
        for (final var name : List.of("TUTORIAL/five-minutes.md", "TUTORIAL/fifteen-minutes.md",
                "BOOK/a-first-rule.md", "BOOK/values.md")) {
            final var document = ROOT.resolve("DOCUMENTATION").resolve(name);
            assertThat(INCLUDE.matcher(Files.readString(document)).results().count())
                    .describedAs("%s should include code from the corpus", name)
                    .isGreaterThan(0);
        }
    }
}
