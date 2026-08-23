package javax0.bubas.analyser.match;

import javax0.bubas.analyser.pattern.*;
import javax0.bubas.api.BubasException;
import javax0.bubas.lexer.LogicalLine;
import javax0.bubas.lexer.Token;
import javax0.bubas.lexer.TokenType;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Matches a logical line against a statement pattern.
 * <p>
 * A pattern matches the <em>whole</em> line or not at all, and matching is a single forward pass
 * with no backtracking. That is possible because every placeholder's appetite is fixed in advance:
 * an {@code identifier} takes one word, a {@code var} takes a word and an index if one follows, a
 * {@code literal} takes an optional sign and one constant, and an {@code expression} runs to the
 * first token that cannot continue one. Registration rejects the patterns that would make this
 * ambiguous, so the matcher never has to guess.
 */
public final class PatternMatcher {

    private final List<Token> tokens;
    private final Vocabulary vocabulary;
    private final Map<String, Binding> bindings = new LinkedHashMap<>();
    private int at;

    private PatternMatcher(LogicalLine line, Vocabulary vocabulary) {
        this.tokens = line.tokens();
        this.vocabulary = vocabulary;
    }

    /** Empty when the line is not this pattern. */
    public static Optional<Match> match(LogicalLine line, StatementPattern pattern,
                                        Vocabulary vocabulary) {
        return new PatternMatcher(line, vocabulary).run(pattern);
    }

    /**
     * The one pattern this line is.
     *
     * @throws BubasException when none matches, or when more than one does. Two patterns matching a
     *                        line is an error rather than something to resolve by precedence: the
     *                        author of the second pattern needs to know, and a silent winner would
     *                        make the language depend on registration order.
     */
    public static Match match(LogicalLine line, Collection<StatementPattern> patterns,
                              Vocabulary vocabulary) {
        final var matches = patterns.stream()
                .map(pattern -> match(line, pattern, vocabulary))
                .flatMap(Optional::stream)
                .toList();
        if (matches.size() == 1) {
            return matches.getFirst();
        }
        throw matches.isEmpty()
                ? noMatch(line, patterns)
                : new BubasException(listing("this line matches more than one pattern",
                matches.stream().map(Match::pattern).toList()), line.line(), line.source());
    }

    /**
     * When nothing matched, say as much as can be said. If the line begins with a word that some
     * pattern also begins with, the author almost certainly meant that statement and got its shape
     * wrong, so naming the shape is far more useful than reporting that the line is unrecognised.
     */
    private static BubasException noMatch(LogicalLine line, Collection<StatementPattern> patterns) {
        final var first = line.tokens().getFirst();
        final var candidates = patterns.stream()
                .filter(p -> p.keyword().filter(first::is).isPresent())
                .toList();
        final String message = switch (candidates.size()) {
            case 0 -> first.type() == TokenType.WORD
                    ? "unknown statement " + first.text()
                    : "no statement matches this line";
            case 1 -> first.text() + " does not match its pattern: " + candidates.getFirst();
            default -> listing(first.text() + " does not match any of its patterns", candidates);
        };
        return new BubasException(message, line.line(), line.source());
    }

    private static String listing(String headline, Collection<StatementPattern> patterns) {
        return patterns.stream()
                .map(pattern -> "\n        " + pattern)
                .collect(Collectors.joining("", headline + ":", ""));
    }

    private Optional<Match> run(StatementPattern pattern) {
        for (final var element : pattern.elements()) {
            if (!element(element)) {
                return Optional.empty();
            }
        }
        return at == tokens.size()
                ? Optional.of(new Match(pattern, Map.copyOf(bindings)))
                : Optional.empty();
    }

    private boolean element(PatternElement element) {
        if (element instanceof Literal literal) {
            return literal(literal);
        }
        final var placeholder = (Placeholder) element;
        return switch (placeholder.kind()) {
            case IDENTIFIER -> identifier(placeholder);
            case VAR -> reference(placeholder);
            case LITERAL -> constant(placeholder);
            case TYPE -> typeName(placeholder);
            case EXPRESSION -> expression(placeholder);
        };
    }

    private boolean literal(Literal literal) {
        if (done()) {
            return false;
        }
        if (!tokens.get(at).is(literal.text())) {
            return false;
        }
        at++;
        return true;
    }

    private boolean identifier(Placeholder placeholder) {
        return name(placeholder).map(token -> bind(new Binding.Name(placeholder, token)))
                .orElse(false);
    }

    private boolean reference(Placeholder placeholder) {
        final var name = name(placeholder);
        if (name.isEmpty()) {
            return false;
        }
        final var index = index();
        return index != null && bind(new Binding.Reference(placeholder, name.get(), index));
    }

    /** One unreserved word, consumed. */
    // AI: Why do we need this argument? Seems to be superfluous.
    private Optional<Token> name(Placeholder placeholder) {
        if (done() || tokens.get(at).type() != TokenType.WORD
                || !vocabulary.isAvailableName(tokens.get(at).text())) {
            return Optional.empty();
        }
        return Optional.of(tokens.get(at++));
    }

    /** The index tokens after a name, empty when unindexed, {@code null} when malformed. */
    private List<Token> index() {
        if (done() || !tokens.get(at).is("[")) {
            return List.of();
        }
        at++;
        final int from = at;
        int depth = 0;
        while (!done() && !(depth == 0 && tokens.get(at).is("]"))) {
            if (tokens.get(at).is("[") || tokens.get(at).is("(")) {
                depth++;
            } else if (tokens.get(at).is("]") || tokens.get(at).is(")")) {
                depth--;
            }
            at++;
        }
        if (done() || from == at) {
            return null;
        }
        final var index = tokens.subList(from, at);
        at++;
        return index;
    }

    private boolean typeName(Placeholder placeholder) {
        if (done() || tokens.get(at).type() != TokenType.WORD
                || !vocabulary.isTypeName(tokens.get(at).text())) {
            return false;
        }
        return bind(new Binding.TypeName(placeholder, tokens.get(at++)));
    }

    /**
     * An optional sign and one constant. The lexer does not produce signed literals, because
     * {@code a-10} and {@code a - 10} must tokenize alike, so the sign is reassembled here — at
     * the only layer that knows a constant is required.
     */
    private boolean constant(Placeholder placeholder) {
        final int start = at;
        Token sign = null;
        if (!done() && (tokens.get(at).is("-") || tokens.get(at).is("+"))) {
            sign = tokens.get(at++);
        }
        if (done() || !admits(placeholder.constraint(), tokens.get(at), sign != null)) {
            at = start;
            return false;
        }
        return bind(new Binding.Constant(placeholder, sign, tokens.get(at++)));
    }

    private boolean admits(Constraint constraint, Token token, boolean signed) {
        final boolean number = token.type() == TokenType.INTEGER || token.type() == TokenType.DECIMAL;
        if (signed && !number) {
            return false;
        }
        if (!(constraint instanceof Constraint.Named named)) {
            return number || token.type() == TokenType.STRING || isBoolean(token);
        }
        return switch (named.name().toUpperCase(java.util.Locale.ROOT)) {
            case "INTEGER" -> token.type() == TokenType.INTEGER;
            case "DECIMAL" -> token.type() == TokenType.DECIMAL;
            case "NUMBER" -> number;
            case "STRING" -> token.type() == TokenType.STRING;
            case "BOOLEAN" -> isBoolean(token);
            default -> false;
        };
    }

    private static boolean isBoolean(Token token) {
        return token.is("TRUE") || token.is("FALSE");
    }

    /** Runs to the first token that cannot continue an expression at bracket depth zero. */
    private boolean expression(Placeholder placeholder) {
        final int from = at;
        int depth = 0;
        while (!done()) {
            final var token = tokens.get(at);
            if (depth == 0 && !continues(token)) {
                break;
            }
            if (token.is("(") || token.is("[")) {
                depth++;
            } else if (token.is(")") || token.is("]")) {
                if (depth == 0) {
                    break;
                }
                depth--;
            }
            at++;
        }
        return at > from && bind(new Binding.Expression(placeholder, tokens.subList(from, at)));
    }

    private boolean continues(Token token) {
        return switch (token.type()) {
            case WORD -> vocabulary.isExpressionWord(token.text());
            case INTEGER, DECIMAL, STRING, OPERATOR -> true;
            case PUNCT -> token.is("(") || token.is("[") || token.is(")") || token.is("]");
        };
    }

    private boolean bind(Binding binding) {
        bindings.put(binding.placeholder().name(), binding);
        return true;
    }

    private boolean done() {
        return at >= tokens.size();
    }
}
