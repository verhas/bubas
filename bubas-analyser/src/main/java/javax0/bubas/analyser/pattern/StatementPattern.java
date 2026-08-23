package javax0.bubas.analyser.pattern;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A parsed statement pattern.
 *
 * @param source   the pattern text as the embedder wrote it, for diagnostics
 * @param elements its literals and placeholders, in order
 */
public record StatementPattern(String source, List<PatternElement> elements) {

    /**
     * The words this pattern reserves. Every word-shaped literal in a pattern becomes a reserved
     * word, not only the leading one: that is what lets an expression end at the first reserved
     * token, so {@code PAY a + b VIA acct} splits without backtracking.
     */
    public Set<String> reservedWords() {
        return elements.stream()
                .filter(Literal.class::isInstance)
                .map(Literal.class::cast)
                .filter(Literal::isWord)
                .map(l -> l.text().toUpperCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * The word this pattern begins with, when it begins with one. Assignment begins with a
     * placeholder and has none, which is why this is optional rather than a plain string.
     */
    public Optional<String> keyword() {
        return elements.getFirst() instanceof Literal first && first.isWord()
                ? Optional.of(first.text())
                : Optional.empty();
    }

    public List<Placeholder> placeholders() {
        return elements.stream()
                .filter(Placeholder.class::isInstance)
                .map(Placeholder.class::cast)
                .toList();
    }

    @Override
    public String toString() {
        return source;
    }
}
