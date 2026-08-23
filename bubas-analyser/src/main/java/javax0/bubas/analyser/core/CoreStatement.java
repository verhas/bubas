package javax0.bubas.analyser.core;

import javax0.bubas.analyser.CommandDefinition;
import javax0.bubas.analyser.FunctionSignature;
import javax0.bubas.api.BubasType;
import javax0.bubas.lexer.LogicalLine;

import java.util.List;
import java.util.Map;

/**
 * A statement with every decision made.
 * <p>
 * The source's variety is already reduced: {@code UNTIL} is a {@code WHILE} over a negated
 * condition, a built-in declaration is an assignment or an allocation, and {@code EXIT} names the
 * loop it leaves by identity rather than by kind. Every back end therefore implements the same
 * handful of shapes.
 * <p>
 * Each node keeps its {@link LogicalLine}, so an interpreter can report a failure against the
 * source and a code generator can emit output that maps back to it — which is what makes debugging
 * generated Java a reasonable substitute for stepping the script.
 */
public sealed interface CoreStatement {

    LogicalLine line();

    /** Writes a variable. */
    record Assign(int slot, CoreExpression value, LogicalLine line) implements CoreStatement {
    }

    /** Writes one array element, bounds-checked. */
    record AssignElement(int slot, CoreExpression index, CoreExpression value, LogicalLine line)
            implements CoreStatement {
    }

    /** Creates an array of {@code size} elements, each holding its type's default. */
    record Allocate(int slot, CoreExpression size, BubasType elementType, LogicalLine line)
            implements CoreStatement {
    }

    record Arm(CoreExpression condition, List<CoreStatement> body) {
    }

    /** @param otherwise {@code null} without an {@code ELSE} */
    record Branch(List<Arm> arms, List<CoreStatement> otherwise, LogicalLine line)
            implements CoreStatement {
    }

    /**
     * @param id        identifies this loop so a {@link Break} can name it
     * @param testAtEnd the body runs at least once
     */
    record Loop(int id, CoreExpression condition, boolean testAtEnd, List<CoreStatement> body,
                LogicalLine line) implements CoreStatement {
    }

    /** A counting loop. Bounds and step are evaluated once, on entry. */
    record Count(int id, int slot, CoreExpression from, CoreExpression to, CoreExpression step,
                 List<CoreStatement> body, LogicalLine line) implements CoreStatement {
    }

    /** Leaves the loop it names. Which loop was decided by lowering, not by kind at run time. */
    record Break(int loopId, LogicalLine line) implements CoreStatement {
    }

    /** @param value {@code null} in a program with no {@code RETURNS} */
    record Return(CoreExpression value, LogicalLine line) implements CoreStatement {
    }

    /** A custom command: the only shape that calls back into embedder code with lazy arguments. */
    record Invoke(CommandDefinition definition, Map<String, CoreArgument> arguments,
                  LogicalLine line) implements CoreStatement {
    }

    /** A bare call to a procedure. Arguments are evaluated left to right, before the call. */
    record Procedure(FunctionSignature signature, List<CoreExpression> arguments, LogicalLine line)
            implements CoreStatement {
    }
}
