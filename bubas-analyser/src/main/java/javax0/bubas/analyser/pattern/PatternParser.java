package javax0.bubas.analyser.pattern;

import javax0.bubas.analyser.Keywords;
import javax0.bubas.api.BubasDefinitionException;
import javax0.bubas.api.BubasException;
import javax0.bubas.lexer.Lexer;
import javax0.bubas.api.TypeNames;
import javax0.bubas.lexer.LogicalLine;
import javax0.bubas.lexer.Token;
import javax0.bubas.lexer.TokenType;

import java.util.*;

/**
 * Turns a pattern string into a {@link StatementPattern}.
 * <p>
 * A pattern is lexed by the same {@link Lexer} that reads source, exactly as written. That is
 * possible because the lexer accepts any punctuation character, so braces are ordinary tokens and
 * a placeholder body arrives already split into tokens. Nothing is rewritten or preprocessed, so a
 * pattern containing any particular word cannot be mistaken for something else, and bracket
 * balance inside a pattern is checked by the lexer for free.
 * <p>
 * Everything checked here is syntactic. Whether {@code /Order} names a registered opaque type or a
 * placeholder in the same pattern cannot be known until the language is sealed, so constraints are
 * captured as written and resolved later.
 */
public final class PatternParser {

    private final String source;
    private final List<Token> tokens;
    private int at;

    private PatternParser(String source) {
        this.source = source;
        this.tokens = lex();
        this.at = 0;
    }

    public static StatementPattern parse(String pattern) {
        return new PatternParser(pattern).run();
    }

    private List<Token> lex() {
        final List<LogicalLine> lines;
        try {
            lines = Lexer.lex(source).stream().filter(LogicalLine::hasTokens).toList();
        } catch (BubasException e) {
            throw error(e.getMessage(), e);
        }
        if (lines.isEmpty()) {
            throw error("a pattern must contain something");
        }
        if (lines.size() > 1) {
            throw error("a pattern must be a single line");
        }
        return lines.getFirst().tokens();
    }

    private StatementPattern run() {
        final var elements = new ArrayList<PatternElement>();
        final var placeholders = new ArrayList<Placeholder>();
        while (at < tokens.size()) {
            if (tokens.get(at).is("{")) {
                at++;
                final var placeholder = placeholder(body());
                placeholders.add(placeholder);
                elements.add(placeholder);
            } else {
                elements.add(new Literal(tokens.get(at).type(), tokens.get(at).text()));
                at++;
            }
        }
        validate(elements, placeholders);
        return new StatementPattern(source, List.copyOf(elements));
    }

    /**
     * The tokens of one placeholder, consuming the closing brace.
     */
    private List<Token> body() {
        final int start = at;
        while (!tokens.get(at).is("}")) {
            if (tokens.get(at).is("{")) {
                throw error("a placeholder may not contain '{'");
            }
            at++;
        }
        final var body = tokens.subList(start, at);
        at++;
        return body;
    }

    // ---------------------------------------------------------------- placeholder body

    /**
     * Splits {@code prefixes > kind[/constraint][:name] > postfixes} on {@code >}. Which zone is
     * which is decided by finding the one that names a kind, so a placeholder carrying only
     * preconditions and one carrying only postconditions are told apart without a positional rule.
     */
    private Placeholder placeholder(List<Token> body) {
        if (body.isEmpty()) {
            throw error("an empty placeholder");
        }
        final var zones = zones(body);
        final int core = getCore(body, zones);
        if (zones.size() > 3) {
            throw error("'" + text(body) + "' has too many '>' zones, maximum 3 allowed");
        }
        if (core > 1 || zones.size() - core > 2) {
            throw error("'" + text(body) + "' core name is displaced. It can be in the first, or the second part.");
        }
        return core(zones.get(core),
                preconditions(body, core == 1 ? zones.getFirst() : List.of()),
                postconditions(body, core < zones.size() - 1 ? zones.getLast() : List.of()));
    }

    /**
     * Get the index of the element in the zones that is the core one, namely the {@code expression}, {@code var}, and
     * so on.
     *
     * @param body  the list of tokens
     * @param zones the list of zones, that is the lis of list of tokens separated by the '{@code >}' characters.
     * @return the index of the zone list that is the core one, namely the {@code expression}, {@code var}, and so on.
     */
    private int getCore(List<Token> body, List<List<Token>> zones) {
        int core = -1;
        for (int i = 0; i < zones.size(); i++) {
            if (!zones.get(i).isEmpty() && Kind.of(zones.get(i).getFirst().text()).isPresent()) {
                if (core >= 0) {
                    throw error("'" + text(body) + "' names more than one kind");
                }
                core = i;
            }
        }
        if (core < 0) {
            throw error("'" + text(body)
                    + "' names no kind; expected one of var, identifier, expression, literal, type");
        }
        return core;
    }

    private List<List<Token>> zones(List<Token> body) {
        final var zones = new ArrayList<List<Token>>();
        int start = 0;
        for (int i = 0; i <= body.size(); i++) {
            if (i == body.size() || body.get(i).is(">")) {
                zones.add(body.subList(start, i));
                start = i + 1;
            }
        }
        return zones;
    }

    private Placeholder core(List<Token> zone, Set<Precondition> pre, Set<Postcondition> post) {
        final var kind = Kind.of(zone.getFirst().text()).orElseThrow();
        int i = 1;
        Constraint constraint = null;
        if (i < zone.size() && zone.get(i).is("/")) {
            i++;
            final int from = i;
            while (i < zone.size() && !zone.get(i).is(":")) {
                i++;
            }
            constraint = constraint(zone.subList(from, i));
        }
        String name = kind.spelling();
        if (i < zone.size()) {
            if (!zone.get(i).is(":")) {
                throw error("'" + text(zone) + "' is not a placeholder");
            }
            i++;
            if (i == zone.size()) {
                throw error("'" + text(zone) + "' has an empty name");
            }
            if (zone.get(i).type() != TokenType.WORD) {
                throw error("'" + zone.get(i).text() + "' is not a valid placeholder name");
            }
            name = zone.get(i).text();
            i++;
            if (i < zone.size()) {
                throw error("'" + text(zone) + "' has more than one name");
            }
        }
        checkName(name);
        return new Placeholder(kind, name, constraint, pre, post);
    }

    private void checkName(String name) {
        if (Precondition.of(name).isPresent() || Postcondition.of(name).isPresent()) {
            throw error("'" + name + "' is a state word and cannot be a placeholder name");
        }
    }

    private Constraint constraint(List<Token> zone) {
        if (zone.isEmpty()) {
            throw error("an empty constraint");
        }
        final var head = zone.getFirst();
        if (head.is(TypeNames.ARRAY)) {
            if (zone.size() == 1) {
                return new Constraint.ArrayOf(null);
            }
            if (!zone.get(1).is("/")) {
                throw error("'" + text(zone) + "' is not a valid constraint");
            }
            return new Constraint.ArrayOf(constraint(zone.subList(2, zone.size())));
        }
        if (head.is("=")) {
            if (zone.size() != 2 || zone.get(1).type() != TokenType.WORD) {
                throw error("'" + text(zone) + "' is not a valid constraint");
            }
            return new Constraint.Named(zone.get(1).text(), true);
        }
        if (head.type() != TokenType.WORD) {
            throw error("'" + text(zone) + "' is not a valid constraint");
        }
        if (zone.size() == 1) {
            return new Constraint.Named(head.text(), false);
        }
        if (zone.size() == 3 && zone.get(1).is("[") && zone.get(2).is("]")) {
            return new Constraint.ElementOf(head.text());
        }
        throw error("'" + text(zone) + "' is not a valid constraint");
    }

    private Set<Precondition> preconditions(List<Token> body, List<Token> zone) {
        final var found = EnumSet.noneOf(Precondition.class);
        for (final var word : conditionWords(zone)) {
            final var p = Precondition.of(word).orElseThrow(
                    () -> error("'" + word + "' is not a precondition"));
            found.stream().filter(other -> other.axis() == p.axis()).findFirst().ifPresent(other -> {
                throw error("'" + text(body) + "' gives two "
                        + p.axis().name().toLowerCase(Locale.ROOT) + " preconditions: "
                        + other.spelling() + " and " + p.spelling());
            });
            found.add(p);
        }
        return Set.copyOf(found);
    }

    private Set<Postcondition> postconditions(List<Token> body, List<Token> zone) {
        final var found = EnumSet.noneOf(Postcondition.class);
        for (final var word : conditionWords(zone)) {
            final var pc = Postcondition.of(word).orElseThrow(() -> error("'" + word + "' is not a postcondition"));
            if (found.contains(pc)) {
                throw error("'" + text(body) + "' gives postconditions " + pc.spelling() + " more than once");
            }
            found.add(pc);
        }
        if (found.contains(Postcondition.FINAL) && found.size() > 1) {
            throw error("'" + text(body) + "' combines 'final' with another postcondition; "
                    + "final already implies the variable is created and initialized");
        }
        return Set.copyOf(found);
    }

    /**
     * A condition zone is {@code WORD (':' WORD)*}.
     */
    private List<String> conditionWords(List<Token> zone) {
        final var words = new ArrayList<String>();
        for (int i = 0; i < zone.size(); i++) {
            final var token = zone.get(i);
            if (i % 2 == 0) {
                if (token.type() != TokenType.WORD) {
                    throw error("'" + token.text() + "' is not a condition");
                }
                words.add(token.text());
            } else if (!token.is(":")) {
                throw error("conditions are separated by ':', not '" + token.text() + "'");
            }
        }
        if (!zone.isEmpty() && zone.size() % 2 == 0) {
            throw error("'" + text(zone) + "' ends with a dangling ':'");
        }
        return words;
    }

    // ---------------------------------------------------------------- whole-pattern rules

    private void validate(List<PatternElement> elements, List<Placeholder> placeholders) {
        if (elements.stream().noneMatch(Literal.class::isInstance)) {
            throw error("a pattern made only of placeholders reserves nothing and would match by "
                    + "shape alone; it needs at least one literal");
        }
        if (elements.getFirst() instanceof Literal first && first.isWord()
                && javax0.bubas.analyser.Keywords.isStructural(first.text())) {
            throw error("'" + first.text().toUpperCase(Locale.ROOT)
                    + "' drives block parsing and cannot begin a pattern; choose another word");
        }
        for (int i = 0; i < elements.size() - 1; i++) {
            if (elements.get(i) instanceof Placeholder p && p.kind() == Kind.VAR
                    && elements.get(i + 1) instanceof Literal next && "[".equals(next.text())) {
                throw error("'" + p.name() + "' is a var followed by a literal '['; given a[1] "
                        + "nothing could say whether the hole takes 'a' or 'a[1]'. "
                        + "Use identifier when the pattern supplies the brackets");
            }
        }
        for (int i = 0; i < elements.size(); i++) {
            if (elements.get(i) instanceof Placeholder p && p.kind() == Kind.EXPRESSION) {
                checkExpressionBoundary(p, i + 1 < elements.size() ? elements.get(i + 1) : null);
            }
        }
        final var names = new HashSet<String>();
        for (final var p : placeholders) {
            if (!names.add(p.name())) {
                throw error("two placeholders are named '" + p.name() + "'");
            }
            validate(p);
        }
    }

    private void validate(Placeholder p) {
        final boolean variableLike = p.kind() == Kind.VAR || p.kind() == Kind.IDENTIFIER;
        if (!variableLike && !(p.preconditions().isEmpty() && p.postconditions().isEmpty())) {
            throw error("only a var or identifier placeholder has conditions; '" + p.name()
                    + "' is a " + p.kind().spelling());
        }
        if (p.kind() == Kind.VAR && p.creates()) {
            throw error("'" + p.name() + "' would create a variable, but a var may be an indexed "
                    + "reference such as a[i], which is not a name; use identifier");
        }
        if (p.kind() == Kind.TYPE && p.constraint() != null) {
            throw error("a type placeholder takes no constraint; '" + p.name() + "' has one");
        }
        if (p.postconditions().contains(Postcondition.FINAL)
                && (p.preconditions().contains(Precondition.DECLARED)
                || p.preconditions().contains(Precondition.INITIALIZED))) {
            throw error("'" + p.name() + "' is made final but required to exist already; "
                    + "a final postcondition implies the variable is new");
        }
        if (p.creates() && p.constraint() == null) {
            throw error("'" + p.name() + "' creates a variable and so must carry a type constraint, "
                    + "otherwise nothing could type its later uses");
        }
    }

    /**
     * An expression runs to the first token that cannot continue one, so whatever follows it in a
     * pattern has to be such a token. Otherwise the expression swallows it and the pattern can
     * never match. An operator or an opening bracket continues an expression, and so does a word
     * like {@code AND} — which is why {@code X {expression:a} AND {expression:b}} is rejected while
     * {@code SELECT 2 FROM {var:a} AND {var:b}} is fine.
     */
    private void checkExpressionBoundary(Placeholder p, PatternElement next) {
        if (next == null) {
            return;
        }
        if (!(next instanceof Literal literal)) {
            throw error("'" + p.name() + "' is an expression followed by another placeholder; "
                    + "nothing would say where the first one ends");
        }
        if (continuesAnExpression(literal)) {
            throw error("'" + p.name() + "' is an expression followed by '" + literal.text()
                    + "', which can itself appear inside an expression, so the expression would "
                    + "swallow it");
        }
    }

    private static boolean continuesAnExpression(Literal literal) {
        if (literal.isWord()) {
            return Keywords.isExpressionWord(literal.text());
        }
        return literal.type() == TokenType.OPERATOR
                || "(".equals(literal.text()) || "[".equals(literal.text());
    }

    // ---------------------------------------------------------------- token helpers

    private static String text(List<Token> tokens) {
        return tokens.stream().map(Token::text).reduce("", String::concat);
    }

    private BubasDefinitionException error(String message) {
        return new BubasDefinitionException("in pattern \"" + source + "\": " + message);
    }

    private BubasDefinitionException error(String message, Throwable cause) {
        return new BubasDefinitionException("in pattern \"" + source + "\": " + message, cause);
    }
}
