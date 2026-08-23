package javax0.bubas.analyser.match;

import javax0.bubas.analyser.pattern.Constraint;
import javax0.bubas.analyser.pattern.Literal;
import javax0.bubas.analyser.pattern.Placeholder;
import javax0.bubas.analyser.pattern.StatementPattern;
import javax0.bubas.lexer.TokenType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A pattern as a finite automaton over {@link TokenClass}es — the language of every token sequence
 * the pattern could match.
 * <p>
 * It is an over-approximation on purpose. An expression is modelled as one-or-more expression
 * tokens with no bracket structure, so the automaton accepts some sequences the matcher would
 * reject. That is the safe direction: overlap analysis may warn about a pair that could never
 * actually collide, but it can never miss a pair that could.
 */
final class PatternAutomaton {

    record Edge(int from, TokenClass on, int to) {
        boolean isEpsilon() {
            return on == null;
        }
    }

    private final List<Edge> edges = new ArrayList<>();
    private int states = 0;
    private final int start;
    private final int accept;

    PatternAutomaton(StatementPattern pattern) {
        start = newState();
        int at = start;
        for (final var element : pattern.elements()) {
            at = element instanceof Literal literal ? literal(at, literal) : placeholder(at, (Placeholder) element);
        }
        accept = at;
    }

    int start() {
        return start;
    }

    int accept() {
        return accept;
    }

    List<Edge> edges() {
        return edges;
    }

    /** Every state reachable from {@code from} without consuming a token. */
    Set<Integer> closure(Set<Integer> from) {
        final var reached = new HashSet<>(from);
        final var pending = new ArrayList<>(from);
        while (!pending.isEmpty()) {
            final int state = pending.removeLast();
            for (final var edge : edges) {
                if (edge.from() == state && edge.isEpsilon() && reached.add(edge.to())) {
                    pending.add(edge.to());
                }
            }
        }
        return reached;
    }

    private int newState() {
        return states++;
    }

    private void edge(int from, TokenClass on, int to) {
        edges.add(new Edge(from, on, to));
    }

    private int literal(int from, Literal literal) {
        final int to = newState();
        edge(from, new TokenClass.Exact(literal.type(), literal.text()), to);
        return to;
    }

    private int placeholder(int from, Placeholder placeholder) {
        return switch (placeholder.kind()) {
            case IDENTIFIER -> single(from, TokenClass.Any.NAME);
            case TYPE -> single(from, TokenClass.Any.TYPE_NAME);
            case EXPRESSION -> oneOrMore(from, TokenClass.Any.EXPRESSION);
            case LITERAL -> constant(from, placeholder);
            case VAR -> reference(from);
        };
    }

    private int single(int from, TokenClass on) {
        final int to = newState();
        edge(from, on, to);
        return to;
    }

    // AI: Why do we need this argument when it is always Any.EXPRESSION?
    private int oneOrMore(int from, TokenClass on) {
        final int to = single(from, on);
        edge(to, on, to);
        return to;
    }

    /** An optional sign, then a constant. Only a numeric constraint admits the sign. */
    private int constant(int from, Placeholder placeholder) {
        final int afterSign = newState();
        edge(from, null, afterSign);
        if (numeric(placeholder.constraint())) {
            edge(from, TokenClass.Any.SIGN, afterSign);
        }
        return single(afterSign, TokenClass.Any.CONSTANT);
    }

    private static boolean numeric(Constraint constraint) {
        if (constraint == null) {
            return true;
        }
        if (!(constraint instanceof Constraint.Named named)) {
            return false;
        }
        return switch (named.name().toUpperCase(Locale.ROOT)) {
            case "INTEGER", "DECIMAL", "NUMBER" -> true;
            default -> false;
        };
    }

    /** A name, then optionally an index in brackets. */
    private int reference(int from) {
        final int name = single(from, TokenClass.Any.NAME);
        final int to = newState();
        edge(name, null, to);
        final int open = newState();
        edge(name, new TokenClass.Exact(TokenType.PUNCT, "["), open);
        final int index = oneOrMore(open, TokenClass.Any.EXPRESSION);
        edge(index, new TokenClass.Exact(TokenType.PUNCT, "]"), to);
        return to;
    }
}
