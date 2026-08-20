package javax0.bubas.lexer;

import javax0.bubas.api.BubasException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Turns BUBAS source text into {@link LogicalLine}s.
 * <p>
 * The lexer owns everything about physical line structure. A physical line continues into the next
 * when a {@code (} or {@code [} opened on it is still unclosed, when it ends with a binary
 * operator or a comma — neither of which can legally end a statement — or when it ends with a
 * lone underscore. Nothing downstream sees a physical line, which is why the specification can say
 * "line" and mean a logical one everywhere else. Diagnostics raised <em>here</em> are the
 * exception: a lexical error names the physical line it occurred on, because that is where the
 * author has to look.
 * <p>
 * The lexer does not know what a keyword is, and cannot: the reserved-word set is not fixed until
 * the language is sealed, and it includes every literal token of every registered pattern, every
 * function name and every opaque type name. Everything word-shaped is a {@link TokenType#WORD}
 * and classification belongs to the analyser. The sole exception is {@code AND}, {@code OR} and
 * {@code MOD}, which the continuation rule must recognise; those are core and can never be
 * extended.
 * <p>
 * Lexing is lossless. Every character of the source lands either in a token or in a
 * {@link Trivia}, so tooling that needs comments, indentation and line breaks can have them
 * without a second scanner that might disagree with this one.
 */
public final class Lexer {

    /** Word-shaped binary operators. A line ending with one of these continues. */
    private static final Set<String> WORD_OPERATORS = Set.of("AND", "OR", "MOD");

    private static final Pattern LINE_BREAK = Pattern.compile("\\R");

    private record Bracket(char open, int line, int column) {
    }

    /** A token under construction; its trailing trivia is not known until the next token appears. */
    private static final class Pending {
        private final TokenType type;
        private final String text;
        private final int line;
        private final int column;
        private final Object value;
        private final List<Trivia> trailing = new ArrayList<>();

        private Pending(TokenType type, String text, int line, int column, Object value) {
            this.type = type;
            this.text = text;
            this.line = line;
            this.column = column;
            this.value = value;
        }

        private Token freeze() {
            return new Token(type, text, line, column, value, List.copyOf(trailing));
        }
    }

    private final String[] lines;
    private final String[] terminators;

    private final List<LogicalLine> result = new ArrayList<>();
    private final List<Pending> pending = new ArrayList<>();
    private final List<Bracket> brackets = new ArrayList<>();

    /** Trivia seen since the last token, awaiting an owner. */
    private final List<Trivia> gap = new ArrayList<>();
    /** Trivia that precedes the first token of the current logical line. */
    private final List<Trivia> lineTrivia = new ArrayList<>();

    private int firstPhysicalLine = -1;
    private int tokensBeforeThisLine;

    private Lexer(String source) {
        final var ls = new ArrayList<String>();
        final var ts = new ArrayList<String>();
        final var m = LINE_BREAK.matcher(source);
        int pos = 0;
        while (m.find()) {
            ls.add(source.substring(pos, m.start()));
            ts.add(m.group());
            pos = m.end();
        }
        // A source ending in a line terminator has no extra empty line after it.
        if (pos < source.length()) {
            ls.add(source.substring(pos));
            ts.add("");
        }
        this.lines = ls.toArray(String[]::new);
        this.terminators = ts.toArray(String[]::new);
    }

    /**
     * @param source the whole source text
     * @return its logical lines, in order, including zero-token lines for blank and comment-only
     * lines; an empty source yields no lines at all
     * @throws BubasException on any lexical error
     */
    public static List<LogicalLine> lex(String source) {
        return new Lexer(source).run();
    }

    private List<LogicalLine> run() {
        for (int ln = 0; ln < lines.length; ln++) {
            tokensBeforeThisLine = pending.size();
            tokenize(ln);
            final boolean explicitContinuation = stripContinuationUnderscore();
            newline(ln);
            if (pending.isEmpty()) {
                emitBlank(ln);
            } else if (!explicitContinuation && brackets.isEmpty()
                    && !continues(pending.getLast())) {
                emit(ln);
            }
        }
        atEndOfInput();
        return List.copyOf(result);
    }

    private void atEndOfInput() {
        if (!brackets.isEmpty()) {
            // The outermost unclosed bracket is the one that swallowed everything after it.
            final var b = brackets.getFirst();
            throw new BubasException("'" + b.open() + "' opened here is never closed",
                    b.line() + 1, lines[b.line()]);
        }
        if (!pending.isEmpty()) {
            throw new BubasException("source ends in the middle of a line",
                    firstPhysicalLine + 1, joined(firstPhysicalLine, lines.length - 1));
        }
    }

    private void newline(int ln) {
        final String terminator = terminators[ln];
        if (!terminator.isEmpty()) {
            gap.add(new Trivia(TriviaType.NEWLINE, terminator, ln + 1, lines[ln].length() + 1));
        }
    }

    /**
     * A lone trailing underscore is a continuation marker rather than an identifier. The ambiguity
     * is real but pathological: it only bites a variable actually named {@code _} that happens to
     * end a line. The marker becomes trivia at the front of the gap, so it precedes any whitespace
     * and the line terminator that follow it.
     */
    private boolean stripContinuationUnderscore() {
        if (pending.size() <= tokensBeforeThisLine) {
            return false;
        }
        final var last = pending.getLast();
        if (last.type == TokenType.WORD && "_".equals(last.text)) {
            pending.removeLast();
            gap.addFirst(new Trivia(TriviaType.CONTINUATION, "_", last.line, last.column));
            return true;
        }
        return false;
    }

    private static boolean continues(Pending t) {
        return switch (t.type) {
            case OPERATOR -> true;
            case PUNCT -> ",".equals(t.text);
            case WORD -> WORD_OPERATORS.contains(t.text.toUpperCase(java.util.Locale.ROOT));
            default -> false;
        };
    }

    /** Hands the pending gap to its owner: the previous token, or the line when there is none. */
    private void flushGap() {
        if (gap.isEmpty()) {
            return;
        }
        if (pending.isEmpty()) {
            lineTrivia.addAll(gap);
        } else {
            pending.getLast().trailing.addAll(gap);
        }
        gap.clear();
    }

    private void emit(int lastPhysicalLine) {
        flushGap();
        result.add(new LogicalLine(firstPhysicalLine + 1,
                joined(firstPhysicalLine, lastPhysicalLine),
                List.copyOf(lineTrivia),
                pending.stream().map(Pending::freeze).toList()));
        reset();
    }

    private void emitBlank(int ln) {
        flushGap();
        result.add(new LogicalLine(ln + 1, lines[ln], List.copyOf(lineTrivia), List.of()));
        reset();
    }

    private void reset() {
        pending.clear();
        lineTrivia.clear();
        gap.clear();
        firstPhysicalLine = -1;
    }

    private String joined(int from, int to) {
        final var sb = new StringBuilder();
        for (int i = from; i <= to; i++) {
            sb.append(lines[i]);
            if (i < to) {
                sb.append(terminators[i]);
            }
        }
        return sb.toString();
    }

    private void tokenize(int ln) {
        final String text = lines[ln];
        int i = 0;
        while (i < text.length()) {
            final char c = text.charAt(i);
            if (c == ' ' || c == '\t') {
                i = whitespace(text, i, ln);
            } else if (c == '\'') {
                gap.add(new Trivia(TriviaType.COMMENT, text.substring(i), ln + 1, i + 1));
                return;
            } else if (isNameStart(c)) {
                i = word(text, i, ln);
            } else if (isDigit(c)) {
                i = number(text, i, ln);
            } else if (c == '"') {
                i = string(text, i, ln);
            } else {
                i = symbol(text, i, ln);
            }
        }
    }

    private int whitespace(String text, int i, int ln) {
        int j = i;
        while (j < text.length() && (text.charAt(j) == ' ' || text.charAt(j) == '\t')) {
            j++;
        }
        gap.add(new Trivia(TriviaType.WHITESPACE, text.substring(i, j), ln + 1, i + 1));
        return j;
    }

    private int word(String text, int i, int ln) {
        int j = i;
        while (j < text.length() && isNamePart(text.charAt(j))) {
            j++;
        }
        add(TokenType.WORD, text.substring(i, j), ln, i, null);
        return j;
    }

    private int number(String text, int i, int ln) {
        int j = i;
        while (j < text.length() && isDigit(text.charAt(j))) {
            j++;
        }
        boolean decimal = false;
        if (j + 1 < text.length() && text.charAt(j) == '.' && isDigit(text.charAt(j + 1))) {
            decimal = true;
            j++;
            while (j < text.length() && isDigit(text.charAt(j))) {
                j++;
            }
        }
        if (j < text.length() && isNameStart(text.charAt(j))) {
            throw error(ln, "malformed number literal '" + text.substring(i, j + 1) + "'");
        }
        final String lexeme = text.substring(i, j);
        if (decimal) {
            add(TokenType.DECIMAL, lexeme, ln, i, new BigDecimal(lexeme));
        } else {
            add(TokenType.INTEGER, lexeme, ln, i, parseLong(lexeme, ln));
        }
        return j;
    }

    private long parseLong(String lexeme, int ln) {
        try {
            return Long.parseLong(lexeme);
        } catch (NumberFormatException e) {
            throw new BubasException("integer literal '" + lexeme + "' does not fit in INTEGER",
                    ln + 1, lines[ln], e);
        }
    }

    private int string(String text, int i, int ln) {
        final var content = new StringBuilder();
        int j = i + 1;
        while (true) {
            if (j >= text.length()) {
                throw error(ln, "unterminated string literal");
            }
            final char c = text.charAt(j);
            if (c == '"') {
                j++;
                break;
            }
            if (c == '\\') {
                if (j + 1 >= text.length()) {
                    throw error(ln, "unterminated string literal");
                }
                content.append(unescape(text.charAt(j + 1), ln));
                j += 2;
            } else {
                content.append(c);
                j++;
            }
        }
        add(TokenType.STRING, text.substring(i, j), ln, i, content.toString());
        return j;
    }

    private char unescape(char escaped, int ln) {
        return switch (escaped) {
            case 'n' -> '\n';
            case 't' -> '\t';
            case 'r' -> '\r';
            case '\\' -> '\\';
            case '"' -> '"';
            default -> throw error(ln, "unknown escape sequence '\\" + escaped + "'");
        };
    }

    private int symbol(String text, int i, int ln) {
        if (i + 1 < text.length()) {
            final String two = text.substring(i, i + 2);
            if (two.equals("<>") || two.equals("<=") || two.equals(">=")) {
                add(TokenType.OPERATOR, two, ln, i, null);
                return i + 2;
            }
        }
        final char c = text.charAt(i);
        switch (c) {
            case '+', '-', '*', '/', '=', '<', '>' -> add(TokenType.OPERATOR, String.valueOf(c), ln, i, null);
            case '(', '[' -> {
                brackets.add(new Bracket(c, ln, i + 1));
                add(TokenType.PUNCT, String.valueOf(c), ln, i, null);
            }
            case ')', ']' -> {
                closeBracket(c, ln);
                add(TokenType.PUNCT, String.valueOf(c), ln, i, null);
            }
            case ',', '.' -> add(TokenType.PUNCT, String.valueOf(c), ln, i, null);
            default -> throw error(ln, "unexpected character '" + c + "'");
        }
        return i + 1;
    }

    private void closeBracket(char close, int ln) {
        final char expected = close == ')' ? '(' : '[';
        if (brackets.isEmpty()) {
            throw error(ln, "unmatched '" + close + "'");
        }
        final var open = brackets.removeLast();
        if (open.open() != expected) {
            throw error(ln, "'" + close + "' does not match '" + open.open()
                    + "' opened on line " + (open.line() + 1));
        }
    }

    private void add(TokenType type, String text, int ln, int index, Object value) {
        flushGap();
        if (pending.isEmpty()) {
            firstPhysicalLine = ln;
        }
        pending.add(new Pending(type, text, ln + 1, index + 1, value));
    }

    private BubasException error(int ln, String message) {
        return new BubasException(message, ln + 1, lines[ln]);
    }

    /**
     * Unicode letters are accepted, not only ASCII. The audience is subject matter experts, whose
     * domain vocabulary is not necessarily English.
     */
    private static boolean isNameStart(char c) {
        return Character.isLetter(c) || c == '_';
    }

    private static boolean isNamePart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }
}
