package javax0.bubas.analyser;

import javax0.bubas.analyser.statement.Program;
import javax0.bubas.analyser.statement.Statement;
import javax0.bubas.analyser.symbol.Variable;
import javax0.bubas.api.BubasType;

import java.util.List;

/**
 * One source, parsed and fully checked.
 * <p>
 * Immutable and reusable: compiling is expensive and running is not, so a program is compiled once
 * and executed as often as wanted, by one {@code Interpreter} per run. Nothing here is resolved by
 * name — every call in the tree already carries its signature and every command its implementation
 * — so execution never consults a registry.
 */
public final class BubasProgram {

    private final BubasLanguage language;
    private final Program program;
    private final List<Variable> variables;

    BubasProgram(BubasLanguage language, Program program, List<Variable> variables) {
        this.language = language;
        this.program = program;
        this.variables = List.copyOf(variables);
    }

    /** Documentation, and not otherwise significant. */
    public String name() {
        return program.name().text();
    }

    /** Supplied by the embedder before the run; FINAL and INITIALIZED on entry. */
    public List<Program.Parameter> parameters() {
        return program.parameters();
    }

    /** {@code null} when the program declares no {@code RETURNS}. */
    public BubasType returns() {
        return program.returns();
    }

    public List<Statement> body() {
        return program.body();
    }

    /** Every variable the program declares, in declaration order, parameters first. */
    public List<Variable> variables() {
        return variables;
    }

    /** The language this was compiled against, which carries the services a run needs. */
    public BubasLanguage language() {
        return language;
    }

    @Override
    public String toString() {
        return "program " + name();
    }
}
