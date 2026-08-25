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

    /**
     * True when matching this pattern brings a variable into existence. Such a statement may appear
     * only at the top level of a program: BUBAS has no local variables, so a declaration inside a
     * block would look scoped while being global.
     */
    public boolean declaresVariable() {
        return placeholders().stream().anyMatch(Placeholder::creates);
    }

    /**
     * The pattern with every placeholder written {@code _} — {@code VALIDATE _ AGAINST _}.
     * <p>
     * This is how a command is named when nothing named it: derived, so it cannot drift from the
     * pattern, and injective in practice, because two patterns sharing a skeleton differ only in
     * placeholder kind and would have to survive overlap analysis first. Literals keep their own
     * spacing so {@code DECLARE _[_] _} still looks like the statement it matches; runs of
     * whitespace collapse so that an embedder's stray double space cannot change the name.
     */
    public String skeleton() {
        final var out = new StringBuilder();
        int depth = 0;
        for (int i = 0; i < source.length(); i++) {
            final var c = source.charAt(i);
            if (c == '{') {
                if (depth++ == 0) {
                    out.append('_');
                }
            } else if (c == '}') {
                depth--;
            } else if (depth == 0) {
                out.append(c);
            }
        }
        return out.toString().replaceAll("\\s+", " ").trim();
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
