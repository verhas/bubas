package javax0.bubas.analyser.match;

import javax0.bubas.api.BubasDefinitionException;
import javax0.bubas.analyser.pattern.StatementPattern;
import javax0.bubas.lexer.TokenType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Rejects pairs of statement patterns that could match the same line.
 * <p>
 * This runs at {@code seal()}, and it has to: whether {@code PAY {expression:a} VIA {var:b}} and
 * {@code PAY {expression:a} FROM {var:b}} can collide depends on {@code FROM} being reserved by
 * the other pattern, which is not known until every pattern is registered.
 * <p>
 * Each pattern becomes a {@link PatternAutomaton} over token classes, and a pair overlaps when the
 * product of their automata can reach a state where both accept — the standard emptiness test for
 * the intersection of two regular languages. The approximation errs towards warning: a pair may be
 * reported that no real line could hit, but no colliding pair can slip through. That is why the
 * check is skippable, not why it should be skipped.
 */
public final class OverlapAnalysis {

    private final Vocabulary vocabulary;

    public OverlapAnalysis(Vocabulary vocabulary) {
        this.vocabulary = vocabulary;
    }

    /**
     * @throws BubasDefinitionException listing every colliding pair, so an embedder registering a
     *                                  vocabulary sees all of its conflicts at once rather than one
     *                                  per attempt
     */
    public void check(List<StatementPattern> patterns) {
        final var automata = patterns.stream().map(PatternAutomaton::new).toList();
        final var conflicts = new ArrayList<String>();
        for (int i = 0; i < patterns.size(); i++) {
            for (int j = i + 1; j < patterns.size(); j++) {
                if (overlap(automata.get(i), automata.get(j))) {
                    conflicts.add("\n        " + patterns.get(i) + "\n        " + patterns.get(j));
                }
            }
        }
        if (!conflicts.isEmpty()) {
            throw new BubasDefinitionException(conflicts.size() == 1
                    ? "these two patterns could match the same line:" + conflicts.getFirst()
                    : conflicts.size() + " pairs of patterns could match the same line:"
                    + String.join("", conflicts));
        }
    }

    /** True when some token sequence is accepted by both. */
    public boolean overlap(StatementPattern left, StatementPattern right) {
        return overlap(new PatternAutomaton(left), new PatternAutomaton(right));
    }

    private boolean overlap(PatternAutomaton left, PatternAutomaton right) {
        record Pair(Set<Integer> left, Set<Integer> right) {
        }
        final var start = new Pair(left.closure(Set.of(left.start())),
                right.closure(Set.of(right.start())));
        final var seen = new HashSet<Pair>();
        final var pending = new ArrayDeque<Pair>();
        seen.add(start);
        pending.add(start);
        while (!pending.isEmpty()) {
            final var pair = pending.remove();
            if (pair.left().contains(left.accept()) && pair.right().contains(right.accept())) {
                return true;
            }
            for (final var next : successors(left, right, pair.left(), pair.right())) {
                final var step = new Pair(next.getFirst(), next.getLast());
                if (seen.add(step)) {
                    pending.add(step);
                }
            }
        }
        return false;
    }

    /** One joint step: every pair of edges whose token classes can both accept one token. */
    private List<List<Set<Integer>>> successors(PatternAutomaton left, PatternAutomaton right,
                                                Set<Integer> from, Set<Integer> to) {
        final var steps = new ArrayList<List<Set<Integer>>>();
        for (final var a : left.edges()) {
            if (a.isEpsilon() || !from.contains(a.from())) {
                continue;
            }
            for (final var b : right.edges()) {
                if (b.isEpsilon() || !to.contains(b.from()) || !compatible(a.on(), b.on())) {
                    continue;
                }
                steps.add(List.of(left.closure(Set.of(a.to())), right.closure(Set.of(b.to()))));
            }
        }
        return steps;
    }

    /** Whether one token could satisfy both classes at once. */
    private boolean compatible(TokenClass a, TokenClass b) {
        // AI: please replace it with record pattern
        if (a instanceof TokenClass.Exact left && b instanceof TokenClass.Exact right) {
            return left.type() == right.type() && left.text().equalsIgnoreCase(right.text());
        }
        if (a instanceof TokenClass.Exact exact) {
            return admits((TokenClass.Any) b, exact);
        }
        if (b instanceof TokenClass.Exact exact) {
            return admits((TokenClass.Any) a, exact);
        }
        return admits((TokenClass.Any) a, (TokenClass.Any) b);
    }

    private boolean admits(TokenClass.Any any, TokenClass.Exact exact) {
        final boolean word = exact.type() == TokenType.WORD;
        return switch (any) {
            case NAME -> word && !vocabulary.isReserved(exact.text());
            case TYPE_NAME -> word && vocabulary.isTypeName(exact.text());
            case CONSTANT -> exact.type() == TokenType.INTEGER || exact.type() == TokenType.DECIMAL
                    || exact.type() == TokenType.STRING
                    || (word && (exact.text().equalsIgnoreCase("TRUE")
                    || exact.text().equalsIgnoreCase("FALSE")));
            case SIGN -> exact.type() == TokenType.OPERATOR
                    && ("+".equals(exact.text()) || "-".equals(exact.text()));
            case EXPRESSION -> word
                    ? vocabulary.isExpressionWord(exact.text())
                    : exact.type() == TokenType.OPERATOR || isBracket(exact.text());
        };
    }

    /**
     * How the open-ended classes overlap each other. A name is an expression on its own, and so is
     * a constant, which is why so many patterns that look distinct are not.
     */
    private static boolean admits(TokenClass.Any a, TokenClass.Any b) {
        if (a == b) {
            return true;
        }
        final var pair = Set.of(a, b);
        if (pair.contains(TokenClass.Any.EXPRESSION)) {
            // A type name is reserved and so never continues an expression.
            return !pair.contains(TokenClass.Any.TYPE_NAME);
        }
        // NAME, TYPE_NAME, CONSTANT and SIGN are pairwise disjoint: names are unreserved words,
        // type names are reserved, constants are literals or TRUE/FALSE, signs are operators.
        return false;
    }

    private static boolean isBracket(String text) {
        return "(".equals(text) || ")".equals(text) || "[".equals(text) || "]".equals(text);
    }
}
