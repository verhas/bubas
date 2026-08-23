package javax0.bubas.analyser.statement;

import javax0.bubas.api.BubasType;
import javax0.bubas.lexer.LogicalLine;
import javax0.bubas.lexer.Token;

import java.util.List;

/**
 * A whole program: one per source.
 *
 * @param name       documentation, and not otherwise significant
 * @param parameters supplied by the embedder before the run; FINAL and INITIALIZED on entry, so a
 *                   program cannot rebind its own inputs
 * @param returns    {@code null} without a {@code RETURNS} clause
 */
public record Program(LogicalLine line, Token name, List<Parameter> parameters, BubasType returns,
                      List<Statement> body) {

    public record Parameter(Token name, BubasType type) {
    }
}
