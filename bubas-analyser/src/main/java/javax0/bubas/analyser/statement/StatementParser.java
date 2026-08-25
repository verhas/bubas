package javax0.bubas.analyser.statement;

import javax0.bubas.analyser.BubasLanguage;
import javax0.bubas.analyser.FunctionSignature;
import javax0.bubas.analyser.expression.Expression;
import javax0.bubas.analyser.expression.ExpressionParser;
import javax0.bubas.analyser.match.Binding;
import javax0.bubas.analyser.match.PatternMatcher;
import javax0.bubas.api.BubasException;
import javax0.bubas.api.BubasType;
import javax0.bubas.lexer.LogicalLine;
import javax0.bubas.lexer.Token;
import javax0.bubas.lexer.TokenType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns logical lines into a {@link Program}.
 * <p>
 * Purely syntactic. It knows the sealed language — for the pattern matcher's vocabulary and for
 * resolving calls — and nothing about which variables exist. Declaredness, types and definite
 * assignment all belong to the pass that walks this tree afterwards, and the parser is testable
 * without any of them.
 * <p>
 * Block structures are recognised here and cannot be extended: no statement pattern may begin with
 * a structural keyword, so a line starting with one of them is never a candidate for matching.
 */
public final class StatementParser {

    private static final Set<String> BLOCK_ENDS = Set.of("END", "ELSEIF", "ELSE");

    private final List<LogicalLine> lines;
    private final BubasLanguage language;
    private int at;
    /** How many blocks deep the parser is. Zero is the program body, where declarations belong. */
    private int depth;

    private StatementParser(List<LogicalLine> lines, BubasLanguage language) {
        this.lines = lines.stream().filter(LogicalLine::hasTokens).toList();
        this.language = language;
    }

    public static Program parse(List<LogicalLine> lines, BubasLanguage language) {
        return new StatementParser(lines, language).program();
    }

    // ------------------------------------------------------------------ program

    private Program program() {
        if (done()) {
            throw new BubasException("a source must contain a program", 1, "");
        }
        final var header = next();
        if (!header.tokens().getFirst().is("PROGRAM")) {
            throw error(header, "a program starts with PROGRAM");
        }
        final var tokens = header.tokens();
        if (tokens.size() < 2 || tokens.get(1).type() != TokenType.WORD) {
            throw error(header, "PROGRAM needs a name");
        }
        int i = 2;
        final var parameters = new ArrayList<Program.Parameter>();
        if (i < tokens.size() && tokens.get(i).is("(")) {
            i = parameters(header, tokens, i + 1, parameters);
        }
        BubasType returns = null;
        if (i < tokens.size()) {
            if (!tokens.get(i).is("RETURNS")) {
                throw error(header, "'" + tokens.get(i).text() + "' does not belong in a PROGRAM header");
            }
            if (i + 2 != tokens.size()) {
                throw error(header, "RETURNS needs exactly one type");
            }
            returns = type(header, tokens.get(i + 1));
        }
        final var body = block(header);
        final var terminator = expect(header, "END");
        if (terminator.tokens().size() > 2
                || (terminator.tokens().size() == 2 && !terminator.tokens().get(1).is("."))) {
            throw error(terminator, "a program ends with END or END.");
        }
        if (!done()) {
            throw error(next(), "a source contains one program; this line follows its END");
        }
        return new Program(header, tokens.get(1), List.copyOf(parameters), returns, body);
    }

    private int parameters(LogicalLine line, List<Token> tokens, int i, List<Program.Parameter> into) {
        if (i < tokens.size() && tokens.get(i).is(")")) {
            throw error(line, "an empty parameter list; omit the parentheses instead");
        }
        while (true) {
            if (i + 1 >= tokens.size() || tokens.get(i).type() != TokenType.WORD) {
                throw error(line, "a parameter is a name followed by a type");
            }
            into.add(new Program.Parameter(tokens.get(i), type(line, tokens.get(i + 1))));
            i += 2;
            if (i < tokens.size() && tokens.get(i).is(",")) {
                i++;
                continue;
            }
            if (i >= tokens.size() || !tokens.get(i).is(")")) {
                throw error(line, "the parameter list is never closed");
            }
            return i + 1;
        }
    }

    // ------------------------------------------------------------------ blocks

    /** The body of a block, one level deeper than where it appears. */
    private List<Statement> nested(LogicalLine opener) {
        depth++;
        try {
            return block(opener);
        } finally {
            depth--;
        }
    }

    /** Statements up to, but not consuming, the line that ends the enclosing block. */
    private List<Statement> block(LogicalLine opener) {
        final var body = new ArrayList<Statement>();
        while (true) {
            if (done()) {
                throw error(opener, "this block is never closed");
            }
            if (BLOCK_ENDS.contains(peek().tokens().getFirst().text().toUpperCase(java.util.Locale.ROOT))) {
                return List.copyOf(body);
            }
            body.add(statement());
        }
    }

    private Statement statement() {
        final var line = peek();
        final var first = line.tokens().getFirst();
        if (first.is("IF")) {
            return ifStatement();
        }
        if (first.is("DO")) {
            return loop();
        }
        if (first.is("FOR")) {
            return forLoop();
        }
        if (first.is("EXIT")) {
            return exit(next());
        }
        if (first.is("RETURN")) {
            return returnStatement(next());
        }
        next();
        final var function = first.type() == TokenType.WORD
                ? language.function(first.text()) : java.util.Optional.<FunctionSignature>empty();
        return function.isPresent() ? call(line, function.get()) : command(line);
    }

    private Statement ifStatement() {
        final var opener = next();
        final var branches = new ArrayList<Statement.Branch>();
        branches.add(new Statement.Branch(opener, condition(opener, 1, "THEN"), nested(opener)));
        List<Statement> otherwise = null;
        while (peek().tokens().getFirst().is("ELSEIF")) {
            final var arm = next();
            branches.add(new Statement.Branch(arm, condition(arm, 1, "THEN"), nested(arm)));
        }
        if (peek().tokens().getFirst().is("ELSE")) {
            final var arm = next();
            if (arm.tokens().size() > 1) {
                throw error(arm, "ELSE takes nothing; write ELSEIF for another condition");
            }
            otherwise = nested(arm);
        }
        closer(expect(opener, "END"), "IF");
        return new Statement.If(opener, List.copyOf(branches), otherwise);
    }

    /** {@code DO WHILE c}, {@code DO UNTIL c}, or a bare {@code DO} with the condition on END DO. */
    private Statement loop() {
        final var opener = next();
        final var head = opener.tokens();
        final boolean testAtStart = head.size() > 1;
        boolean until = false;
        Expression condition = null;
        if (testAtStart) {
            until = head.get(1).is("UNTIL");
            if (!until && !head.get(1).is("WHILE")) {
                throw error(opener, "DO is followed by WHILE, UNTIL, or nothing");
            }
            condition = expression(opener, head.subList(2, head.size()));
        }
        final var body = nested(opener);
        final var closer = expect(opener, "END");
        final var end = closer.tokens();
        if (end.size() < 2 || !end.get(1).is("DO")) {
            throw error(closer, "expected END DO");
        }
        if (testAtStart) {
            if (end.size() > 2) {
                throw error(closer, "this loop already tests its condition at DO");
            }
            return new Statement.Loop(opener, condition, until, false, body);
        }
        if (end.size() < 3) {
            throw error(closer, "this loop tests its condition nowhere; put WHILE or UNTIL on DO "
                    + "or on END DO");
        }
        until = end.get(2).is("UNTIL");
        if (!until && !end.get(2).is("WHILE")) {
            throw error(closer, "END DO is followed by WHILE, UNTIL, or nothing");
        }
        return new Statement.Loop(opener, expression(closer, end.subList(3, end.size())), until,
                true, body);
    }

    private Statement forLoop() {
        final var opener = next();
        final var tokens = opener.tokens();
        if (tokens.size() < 3 || tokens.get(1).type() != TokenType.WORD || !tokens.get(2).is("=")) {
            throw error(opener, "FOR needs a variable and an initial value: FOR i = 0 TO 4");
        }
        final int to = find(tokens, "TO", 3);
        if (to < 0) {
            throw error(opener, "FOR needs TO");
        }
        final int step = find(tokens, "STEP", to + 1);
        final var from = expression(opener, tokens.subList(3, to));
        final var bound = expression(opener, tokens.subList(to + 1, step < 0 ? tokens.size() : step));
        final var by = step < 0 ? null
                : expression(opener, tokens.subList(step + 1, tokens.size()));
        final var body = nested(opener);
        closer(expect(opener, "END"), "FOR");
        return new Statement.For(opener, tokens.get(1), from, bound, by, body);
    }

    private Statement exit(LogicalLine line) {
        final var tokens = line.tokens();
        if (tokens.size() != 2 || !(tokens.get(1).is("FOR") || tokens.get(1).is("DO"))) {
            throw error(line, "EXIT names the loop it leaves: EXIT FOR or EXIT DO");
        }
        return new Statement.Exit(line, tokens.get(1).is("FOR"));
    }

    private Statement returnStatement(LogicalLine line) {
        final var tokens = line.tokens();
        return new Statement.Return(line, tokens.size() == 1 ? null
                : expression(line, tokens.subList(1, tokens.size())));
    }

    // ------------------------------------------------------------------ ordinary lines

    private Statement call(LogicalLine line, FunctionSignature signature) {
        if (signature.returnType() != BubasType.VOID) {
            throw error(line, signature.name() + " returns a value, so it cannot stand alone as a "
                    + "statement; a result would be discarded silently");
        }
        final var tokens = line.tokens();
        var arguments = tokens.subList(1, tokens.size());
        if (!arguments.isEmpty() && arguments.getFirst().is("(")
                && arguments.getLast().is(")")) {
            arguments = arguments.subList(1, arguments.size() - 1);
        }
        final var parsed = new ArrayList<Expression>();
        for (final var span : split(arguments)) {
            parsed.add(expression(line, span));
        }
        if (!signature.accepts(parsed.size())) {
            throw error(line, signature.arityComplaint(parsed.size()));
        }
        return new Statement.Call(line, signature, List.copyOf(parsed));
    }

    private Statement command(LogicalLine line) {
        final var patterns = language.commands().stream()
                .map(javax0.bubas.analyser.CommandDefinition::pattern).toList();
        final var match = PatternMatcher.match(line, patterns, language.vocabulary());
        final var definition = language.commands().stream()
                .filter(candidate -> candidate.pattern().equals(match.pattern()))
                .findFirst().orElseThrow();
        if (depth > 0 && definition.pattern().declaresVariable()) {
            throw error(line, "'" + line.tokens().getFirst().text() + "' declares a variable, and a "
                    + "variable may only be declared at the top level of a program. BUBAS has no "
                    + "local variables, so a declaration inside a block would look scoped while "
                    + "being global");
        }
        final var arguments = new LinkedHashMap<String, Argument>();
        match.bindings().forEach((name, binding) -> arguments.put(name, argument(line, binding)));
        return new Statement.Command(line, definition, Map.copyOf(arguments));
    }

    private Argument argument(LogicalLine line, Binding binding) {
        return switch (binding) {
            case Binding.Name name -> new Argument.Name(name.token());
            case Binding.Reference reference -> new Argument.Reference(reference.name(),
                    reference.index().isEmpty() ? null : expression(line, reference.index()));
            case Binding.Expression expression -> new Argument.Expr(
                    expression(line, expression.tokens()));
            case Binding.Constant constant -> new Argument.Constant(constant.token(),
                    signed(constant.sign(), constant.token().value()));
            case Binding.TypeName type -> new Argument.TypeName(type.token(),
                    type(line, type.token()));
        };
    }

    private static Object signed(Token sign, Object value) {
        if (sign == null || sign.is("+")) {
            return value;
        }
        return value instanceof BigDecimal decimal ? decimal.negate() : -(Long) value;
    }

    // ------------------------------------------------------------------ helpers

    private BubasType type(LogicalLine line, Token token) {
        return switch (token.text().toUpperCase(java.util.Locale.ROOT)) {
            case "INTEGER" -> BubasType.INTEGER;
            case "DECIMAL" -> BubasType.DECIMAL;
            case "STRING" -> BubasType.STRING;
            case "BOOLEAN" -> BubasType.BOOLEAN;
            default -> language.opaqueType(token.text()).orElseThrow(() ->
                    error(line, "'" + token.text() + "' is not a type"));
        };
    }

    /** The expression between token {@code from} and a required trailing keyword. */
    private Expression condition(LogicalLine line, int from, String trailer) {
        final var tokens = line.tokens();
        if (tokens.size() < from + 2 || !tokens.getLast().is(trailer)) {
            throw error(line, "expected " + trailer + " at the end of this line");
        }
        return expression(line, tokens.subList(from, tokens.size() - 1));
    }

    private Expression expression(LogicalLine line, List<Token> tokens) {
        return ExpressionParser.parse(line, tokens, language);
    }

    /** Splits a comma-separated argument list at bracket depth zero. */
    private static List<List<Token>> split(List<Token> tokens) {
        final var spans = new ArrayList<List<Token>>();
        if (tokens.isEmpty()) {
            return spans;
        }
        int depth = 0;
        int start = 0;
        for (int i = 0; i < tokens.size(); i++) {
            final var token = tokens.get(i);
            if (token.is("(") || token.is("[")) {
                depth++;
            } else if (token.is(")") || token.is("]")) {
                depth--;
            } else if (depth == 0 && token.is(",")) {
                spans.add(tokens.subList(start, i));
                start = i + 1;
            }
        }
        spans.add(tokens.subList(start, tokens.size()));
        return spans;
    }

    private static int find(List<Token> tokens, String text, int from) {
        int depth = 0;
        for (int i = from; i < tokens.size(); i++) {
            final var token = tokens.get(i);
            if (token.is("(") || token.is("[")) {
                depth++;
            } else if (token.is(")") || token.is("]")) {
                depth--;
            } else if (depth == 0 && token.type() == TokenType.WORD && token.is(text)) {
                return i;
            }
        }
        return -1;
    }

    private void closer(LogicalLine line, String what) {
        if (line.tokens().size() != 2 || !line.tokens().get(1).is(what)) {
            throw error(line, "expected END " + what);
        }
    }

    private LogicalLine expect(LogicalLine opener, String word) {
        if (done()) {
            throw error(opener, "this block is never closed");
        }
        final var line = next();
        if (!line.tokens().getFirst().is(word)) {
            throw error(line, "expected " + word);
        }
        return line;
    }

    private boolean done() {
        return at >= lines.size();
    }

    private LogicalLine peek() {
        return lines.get(at);
    }

    private LogicalLine next() {
        return lines.get(at++);
    }

    private static BubasException error(LogicalLine line, String message) {
        return new BubasException(message, line.line(), line.source());
    }
}
