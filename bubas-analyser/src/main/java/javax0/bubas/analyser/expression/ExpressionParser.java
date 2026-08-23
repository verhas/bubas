package javax0.bubas.analyser.expression;

import javax0.bubas.analyser.BubasLanguage;
import javax0.bubas.analyser.FunctionSignature;
import javax0.bubas.api.BubasException;
import javax0.bubas.api.BubasType;
import javax0.bubas.lexer.LogicalLine;
import javax0.bubas.lexer.Token;
import javax0.bubas.lexer.TokenType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Parses a token span into an {@link Expression}.
 * <p>
 * The span arrives already delimited. Working out where an expression ends is the pattern matcher's
 * job, and it is the reason every literal token of every pattern is a reserved word: an expression
 * stops at the first token that cannot continue one. So this parser never looks past its span, and
 * never backtracks.
 * <p>
 * It builds structure and checks arity against the sealed signatures. Types are deliberately not
 * computed here: an expression's type needs the types of the variables in it, which is the symbol
 * table's business, and keeping the typing rules in one later pass beats scattering them through
 * six precedence levels.
 */
public final class ExpressionParser {

    private static final Set<String> ADDITIVE = Set.of("+", "-");
    private static final Set<String> MULTIPLICATIVE = Set.of("*", "/");
    private static final Set<String> COMPARISON = Set.of("=", "<>", "<", ">", "<=", ">=");

    private final LogicalLine line;
    private final List<Token> tokens;
    private final BubasLanguage language;
    private int at;

    private ExpressionParser(LogicalLine line, List<Token> tokens, BubasLanguage language) {
        this.line = line;
        this.tokens = tokens;
        this.language = language;
    }

    /**
     * @param line     the logical line the span came from, for diagnostics
     * @param tokens   the span, already delimited by the matcher
     * @param language the sealed language, for resolving calls
     */
    public static Expression parse(LogicalLine line, List<Token> tokens, BubasLanguage language) {
        final var parser = new ExpressionParser(line, tokens, language);
        if (tokens.isEmpty()) {
            throw parser.error(null, "an expression is missing");
        }
        final var expression = parser.or();
        if (parser.at < tokens.size()) {
            throw parser.error(tokens.get(parser.at),
                    "'" + tokens.get(parser.at).text() + "' does not belong to this expression");
        }
        return expression;
    }

    // ------------------------------------------------------------------ precedence levels

    private Expression or() {
        var left = and();
        while (word("OR")) {
            final var operator = tokens.get(at++);
            left = new Expression.Binary(operator, left, and());
        }
        return left;
    }

    private Expression and() {
        var left = comparison();
        while (word("AND")) {
            final var operator = tokens.get(at++);
            left = new Expression.Binary(operator, left, comparison());
        }
        return left;
    }

    private Expression comparison() {
        var left = additive();
        while (symbol(COMPARISON)) {
            final var operator = tokens.get(at++);
            left = new Expression.Binary(operator, left, additive());
        }
        return left;
    }

    private Expression additive() {
        var left = multiplicative();
        while (symbol(ADDITIVE)) {
            final var operator = tokens.get(at++);
            left = new Expression.Binary(operator, left, multiplicative());
        }
        return left;
    }

    private Expression multiplicative() {
        var left = unary();
        while (symbol(MULTIPLICATIVE) || word("MOD")) {
            final var operator = tokens.get(at++);
            left = new Expression.Binary(operator, left, unary());
        }
        return left;
    }

    private Expression unary() {
        if (word("NOT") || symbol(ADDITIVE)) {
            final var operator = tokens.get(at++);
            return new Expression.Unary(operator, unary());
        }
        return primary();
    }

    // ------------------------------------------------------------------ leaves

    private Expression primary() {
        if (done()) {
            throw error(null, "an expression ends unfinished");
        }
        final var token = tokens.get(at);
        return switch (token.type()) {
            case INTEGER, DECIMAL, STRING -> constant(token);
            case WORD -> wordLeaf(token);
            case PUNCT -> token.is("(") ? group() : unexpected(token);
            default -> unexpected(token);
        };
    }

    private Expression constant(Token token) {
        at++;
        return new Expression.Constant(token, token.value());
    }

    private Expression group() {
        at++;
        final var inner = or();
        if (done() || !tokens.get(at).is(")")) {
            throw error(done() ? null : tokens.get(at), "a '(' is never closed in this expression");
        }
        at++;
        return inner;
    }

    private Expression wordLeaf(Token token) {
        if (token.is("TRUE") || token.is("FALSE")) {
            at++;
            return new Expression.Constant(token, token.is("TRUE"));
        }
        final var function = language.function(token.text());
        if (function.isPresent()) {
            return call(token, function.get());
        }
        if (!language.vocabulary().isAvailableName(token.text())) {
            throw error(token, "'" + token.text() + "' is reserved and cannot appear in an "
                    + "expression");
        }
        at++;
        if (!done() && tokens.get(at).is("[")) {
            at++;
            final var index = or();
            if (done() || !tokens.get(at).is("]")) {
                throw error(done() ? null : tokens.get(at),
                        "an index opened on '" + token.text() + "' is never closed");
            }
            at++;
            return new Expression.Indexed(token, index);
        }
        return new Expression.Variable(token);
    }

    /**
     * Parentheses are mandatory on a call inside an expression, and a procedure has no value to
     * contribute, so both are rejected here rather than left to the type checker.
     */
    private Expression call(Token token, FunctionSignature signature) {
        at++;
        if (signature.returnType() == BubasType.VOID) {
            throw error(token, signature.name() + " returns nothing, so it cannot be used in an "
                    + "expression");
        }
        if (done() || !tokens.get(at).is("(")) {
            throw error(token, signature.name()
                    + " is a function; a call needs parentheses, even with no arguments");
        }
        at++;
        final var arguments = new ArrayList<Expression>();
        if (!done() && tokens.get(at).is(")")) {
            at++;
        } else {
            while (true) {
                arguments.add(or());
                if (!done() && tokens.get(at).is(",")) {
                    at++;
                    continue;
                }
                if (done() || !tokens.get(at).is(")")) {
                    throw error(done() ? null : tokens.get(at),
                            "the argument list of " + signature.name() + " is never closed");
                }
                at++;
                break;
            }
        }
        if (arguments.size() != signature.parameters().size()) {
            throw error(token, signature.name() + " takes " + signature.parameters().size()
                    + " argument(s) but was given " + arguments.size() + ": " + signature);
        }
        return new Expression.Call(token, signature, List.copyOf(arguments));
    }

    private Expression unexpected(Token token) {
        throw error(token, "'" + token.text() + "' cannot appear in an expression");
    }

    // ------------------------------------------------------------------ token helpers

    private boolean done() {
        return at >= tokens.size();
    }

    private boolean word(String text) {
        return !done() && tokens.get(at).type() == TokenType.WORD && tokens.get(at).is(text);
    }

    private boolean symbol(Set<String> texts) {
        return !done() && tokens.get(at).type() == TokenType.OPERATOR
                && texts.contains(tokens.get(at).text());
    }

    private BubasException error(Token token, String message) {
        return new BubasException(token == null ? message
                : message + " (at " + token.line() + ":" + token.column() + ")",
                line.line(), line.source());
    }
}
