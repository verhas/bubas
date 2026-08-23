package javax0.bubas.analyser.statement;

import javax0.bubas.analyser.CommandDefinition;
import javax0.bubas.analyser.FunctionSignature;
import javax0.bubas.analyser.expression.Expression;
import javax0.bubas.lexer.LogicalLine;
import javax0.bubas.lexer.Token;

import java.util.List;
import java.util.Map;

/**
 * One statement.
 * <p>
 * Every node carries the {@link LogicalLine} it came from, so any later diagnostic can quote the
 * source the author actually wrote — joined continuation lines and all.
 */
public sealed interface Statement {

    LogicalLine line();

    /** An {@code IF} with its {@code ELSEIF} arms; {@code otherwise} is null without an {@code ELSE}. */
    record If(LogicalLine line, List<Branch> branches, List<Statement> otherwise)
            implements Statement {
    }

    record Branch(LogicalLine line, Expression condition, List<Statement> body) {
    }

    /**
     * A {@code DO} loop.
     *
     * @param until     {@code UNTIL} rather than {@code WHILE}: the condition is inverted
     * @param testAtEnd the condition sits on {@code END DO}, so the body always runs at least once
     *                  — which is what lets such a loop satisfy a definite-assignment obligation
     */
    record Loop(LogicalLine line, Expression condition, boolean until, boolean testAtEnd,
                List<Statement> body) implements Statement {
    }

    /**
     * @param step {@code null} when no {@code STEP} was written, meaning one
     */
    record For(LogicalLine line, Token variable, Expression from, Expression to, Expression step,
               List<Statement> body) implements Statement {
    }

    /** {@code EXIT FOR} or {@code EXIT DO}: each leaves the innermost enclosing loop of that kind. */
    record Exit(LogicalLine line, boolean fromFor) implements Statement {
    }

    /** @param value {@code null} in a program with no {@code RETURNS} clause */
    record Return(LogicalLine line, Expression value) implements Statement {
    }

    /** A line that matched a registered statement pattern. */
    record Command(LogicalLine line, CommandDefinition definition, Map<String, Argument> arguments)
            implements Statement {
    }

    /** A bare call to a procedure, with or without parentheses. */
    record Call(LogicalLine line, FunctionSignature signature, List<Expression> arguments)
            implements Statement {
    }
}
