package javax0.bubas.analyser.pattern;

import javax0.bubas.api.BubasDefinitionException;
import javax0.bubas.lexer.Lexer;
import javax0.bubas.lexer.TokenType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Turns a pattern string into a {@link StatementPattern}.
 * <p>
 * The literal text between placeholders is lexed with the same {@link Lexer} that reads source, so
 * a pattern literal can only ever be a token a source line could contain, and the two can be
 * compared directly. To make that work the placeholders are first replaced by marker names, which
 * also means the pattern's brackets are checked for balance by the lexer for free.
 * <p>
 * Everything checked here is syntactic. Whether {@code /Order} names a registered opaque type or a
 * placeholder in the same pattern cannot be known until the language is sealed, so constraints are
 * captured as written and resolved later.
 */
public final class PatternParser {

    private static final String MARKER_PREFIX = "__bubas_ph_";
    private static final String MARKER_SUFFIX = "__";

    private final String source;
    private final List<String> bodies = new ArrayList<>();

    private PatternParser(String source) {
        this.source = source;
    }

    public static StatementPattern parse(String pattern) {
        return new PatternParser(pattern).run();
    }

    private StatementPattern run() {
        final var elements = new ArrayList<PatternElement>();
        final var placeholders = new ArrayList<Placeholder>();
        for (final var token : lex(rewrite())) {
            final int index = markerIndex(token.type(), token.text());
            if (index < 0) {
                elements.add(new Literal(token.type(), token.text()));
            } else {
                final var placeholder = placeholder(bodies.get(index));
                placeholders.add(placeholder);
                elements.add(placeholder);
            }
        }
        validate(elements, placeholders);
        return new StatementPattern(source, List.copyOf(elements));
    }

    /** Replaces every {@code {...}} with a marker name, collecting the bodies in order. */
    private String rewrite() {
        final var rewritten = new StringBuilder();
        int i = 0;
        while (i < source.length()) {
            final char c = source.charAt(i);
            if (c == '}') {
                throw error("'}' without a matching '{'");
            }
            if (c != '{') {
                rewritten.append(c);
                i++;
                continue;
            }
            final int close = source.indexOf('}', i + 1);
            if (close < 0) {
                throw error("'{' is never closed");
            }
            final String body = source.substring(i + 1, close);
            if (body.indexOf('{') >= 0) {
                throw error("a placeholder may not contain '{'");
            }
            rewritten.append(MARKER_PREFIX).append(bodies.size()).append(MARKER_SUFFIX);
            bodies.add(body);
            i = close + 1;
        }
        return rewritten.toString();
    }

    private List<javax0.bubas.lexer.Token> lex(String rewritten) {
        final List<javax0.bubas.lexer.LogicalLine> lines;
        try {
            lines = Lexer.lex(rewritten).stream().filter(l -> l.hasTokens()).toList();
        } catch (RuntimeException e) {
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

    private int markerIndex(TokenType type, String text) {
        if (type != TokenType.WORD || !text.startsWith(MARKER_PREFIX) || !text.endsWith(MARKER_SUFFIX)) {
            return -1;
        }
        final String digits = text.substring(MARKER_PREFIX.length(),
                text.length() - MARKER_SUFFIX.length());
        return digits.chars().allMatch(Character::isDigit) ? Integer.parseInt(digits) : -1;
    }

    // ---------------------------------------------------------------- placeholder body

    /**
     * Splits {@code prefixes > kind[/constraint][:name] > postfixes} on {@code >}. Which part is
     * which is decided by finding the one that names a kind, so a placeholder with only prefixes
     * and one with only postfixes are told apart without a positional rule.
     */
    private Placeholder placeholder(String body) {
        final var zones = split(body, '>');
        int core = -1;
        for (int i = 0; i < zones.size(); i++) {
            if (Kind.of(kindWordOf(zones.get(i))).isPresent()) {
                if (core >= 0) {
                    throw error("'" + body + "' names more than one kind");
                }
                core = i;
            }
        }
        if (core < 0) {
            throw error("'" + body
                    + "' names no kind; expected one of var, identifier, expression, literal, type");
        }
        if (zones.size() > 3 || core > 1 || zones.size() - core > 2) {
            throw error("'" + body + "' has too many '>' zones");
        }
        final var preconditions = preconditions(body, core > 0 ? zones.getFirst() : "");
        final var postconditions = postconditions(body, core < zones.size() - 1 ? zones.getLast() : "");
        return core(body, zones.get(core), preconditions, postconditions);
    }

    /** The first colon-segment of a zone, which is where a kind word would sit. */
    private static String kindWordOf(String zone) {
        final var head = split(zone, ':').getFirst();
        final int slash = head.indexOf('/');
        return slash < 0 ? head : head.substring(0, slash);
    }

    private Placeholder core(String body, String zone,
                             Set<Precondition> pre, Set<Postcondition> post) {
        final var segments = split(zone, ':');
        if (segments.size() > 2) {
            throw error("'" + body + "' has more than one name");
        }
        final String head = segments.getFirst();
        final int slash = head.indexOf('/');
        final var kind = Kind.of(slash < 0 ? head : head.substring(0, slash)).orElseThrow();
        final var constraint = slash < 0 ? null : constraint(body, head.substring(slash + 1));
        final String name = segments.size() == 2 ? segments.get(1) : kind.spelling();
        checkName(body, name);
        return new Placeholder(kind, name, constraint, pre, post);
    }

    private void checkName(String body, String name) {
        if (name.isEmpty()) {
            throw error("'" + body + "' has an empty name");
        }
        if (Precondition.of(name).isPresent() || Postcondition.of(name).isPresent()) {
            throw error("'" + name + "' is a state word and cannot be a placeholder name");
        }
        if (!Character.isLetter(name.charAt(0)) && name.charAt(0) != '_') {
            throw error("'" + name + "' is not a valid placeholder name");
        }
    }

    private Constraint constraint(String body, String text) {
        if (text.isEmpty()) {
            throw error("'" + body + "' has an empty constraint");
        }
        final int slash = text.indexOf('/');
        final String head = slash < 0 ? text : text.substring(0, slash);
        if (head.equalsIgnoreCase("ARRAY")) {
            return new Constraint.ArrayOf(slash < 0 ? null : constraint(body, text.substring(slash + 1)));
        }
        if (slash >= 0) {
            throw error("'" + text + "' is not a valid constraint");
        }
        if (text.endsWith("[]")) {
            return new Constraint.ElementOf(text.substring(0, text.length() - 2));
        }
        return text.startsWith("=")
                ? new Constraint.Named(text.substring(1), true)
                : new Constraint.Named(text, false);
    }

    private Set<Precondition> preconditions(String body, String zone) {
        final var found = EnumSet.noneOf(Precondition.class);
        for (final var word : words(zone)) {
            final var p = Precondition.of(word).orElseThrow(
                    () -> error("'" + word + "' is not a precondition"));
            found.stream().filter(other -> other.axis() == p.axis()).findFirst().ifPresent(other -> {
                throw error("'" + body + "' gives two " + p.axis().name().toLowerCase(Locale.ROOT)
                        + " preconditions: " + other.spelling() + " and " + p.spelling());
            });
            found.add(p);
        }
        return Set.copyOf(found);
    }

    private Set<Postcondition> postconditions(String body, String zone) {
        final var found = EnumSet.noneOf(Postcondition.class);
        for (final var word : words(zone)) {
            found.add(Postcondition.of(word).orElseThrow(
                    () -> error("'" + word + "' is not a postcondition")));
        }
        if (found.contains(Postcondition.FINAL) && found.size() > 1) {
            throw error("'" + body + "' combines 'final' with another postcondition; "
                    + "final already implies the variable is created and initialized");
        }
        return Set.copyOf(found);
    }

    private static List<String> words(String zone) {
        return zone.isBlank() ? List.of() : split(zone, ':');
    }

    /** Splits on a separator, trimming each part; spaces around ':' and '>' carry no meaning. */
    private static List<String> split(String text, char separator) {
        final var parts = new ArrayList<String>();
        int start = 0;
        for (int i = 0; i <= text.length(); i++) {
            if (i == text.length() || text.charAt(i) == separator) {
                parts.add(text.substring(start, i).trim());
                start = i + 1;
            }
        }
        return parts;
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
        if (p.kind() == Kind.VAR && creates(p)) {
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
        if (creates(p) && p.constraint() == null) {
            throw error("'" + p.name() + "' creates a variable and so must carry a type constraint, "
                    + "otherwise nothing could type its later uses");
        }
    }

    /** A placeholder creates a variable when it says {@code new}, or implies it by making one final. */
    private static boolean creates(Placeholder p) {
        return p.preconditions().contains(Precondition.NEW)
                || p.postconditions().contains(Postcondition.FINAL);
    }

    private BubasDefinitionException error(String message) {
        return new BubasDefinitionException("in pattern \"" + source + "\": " + message);
    }

    private BubasDefinitionException error(String message, Throwable cause) {
        return new BubasDefinitionException("in pattern \"" + source + "\": " + message, cause);
    }
}
