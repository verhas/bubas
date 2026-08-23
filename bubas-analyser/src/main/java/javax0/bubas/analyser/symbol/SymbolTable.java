package javax0.bubas.analyser.symbol;

import javax0.bubas.analyser.Keywords;
import javax0.bubas.analyser.match.Vocabulary;
import javax0.bubas.api.BubasException;
import javax0.bubas.api.BubasType;
import javax0.bubas.lexer.LogicalLine;
import javax0.bubas.lexer.Token;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The variables a program declares.
 * <p>
 * There is one scope. A name is unique case-insensitively and must be written exactly as declared,
 * so {@code userId} and {@code UserID} cannot coexist and neither can stand in for the other — the
 * rule that keeps a lookalike pair from ever reaching a reader. Names share one namespace with
 * keywords, function names and opaque type names, so a variable may not be called {@code order}
 * when {@code Order} is a registered type.
 * <p>
 * Declaredness needs no flow analysis. A declaration may appear only at the top level of a program,
 * so it always runs, on every path, before anything that could use it: a name is known to every
 * line after its declaration and to none before. Whether the variable then holds a value is
 * {@link Assignment}'s business, and that is the only part that varies by path.
 */
public final class SymbolTable {

    private final Vocabulary vocabulary;
    private final Map<String, Variable> byCanonicalName = new LinkedHashMap<>();
    private final Set<String> read = new LinkedHashSet<>();

    public SymbolTable(Vocabulary vocabulary) {
        this.vocabulary = vocabulary;
    }

    /**
     * @throws BubasException when the name is reserved, or collides with one already declared
     */
    public Variable declare(LogicalLine line, Token name, BubasType type, boolean isFinal) {
        final var canonical = Keywords.canonical(name.text());
        if (vocabulary.isTypeName(name.text())) {
            throw error(line, name, "'" + name.text() + "' is a type name; a variable may not be "
                    + "named after its type");
        }
        if (vocabulary.isReserved(name.text())) {
            throw error(line, name, "'" + name.text() + "' is reserved and cannot name a variable");
        }
        final var existing = byCanonicalName.get(canonical);
        if (existing != null) {
            throw error(line, name, existing.name().equals(name.text())
                    ? "'" + name.text() + "' is already declared on line " + existing.declaredAt().line()
                    : "'" + name.text() + "' collides with '" + existing.name() + "', declared on line "
                    + existing.declaredAt().line() + "; names are unique ignoring case");
        }
        final var variable = new Variable(name.text(), type, isFinal, name);
        byCanonicalName.put(canonical, variable);
        return variable;
    }

    /**
     * Resolves a reference and records that the variable was read.
     *
     * @throws BubasException when the name is undeclared, or spelled differently from its
     *                        declaration
     */
    public Variable reference(LogicalLine line, Token name) {
        final var variable = resolve(line, name);
        read.add(Keywords.canonical(name.text()));
        return variable;
    }

    /**
     * Resolves without recording a read, for an assignment target: writing to a variable is not
     * using it, and a variable only ever written is still a variable nobody needed.
     *
     * @throws BubasException when the name is undeclared, or spelled differently from its
     *                        declaration
     */
    public Variable resolve(LogicalLine line, Token name) {
        final var variable = byCanonicalName.get(Keywords.canonical(name.text()));
        if (variable == null) {
            throw error(line, name, "'" + name.text() + "' is not declared");
        }
        if (!variable.name().equals(name.text())) {
            throw error(line, name, "'" + name.text() + "' is declared as '" + variable.name()
                    + "' on line " + variable.declaredAt().line()
                    + "; every reference must match the declaration character for character");
        }
        return variable;
    }

    /** Without recording a read: for an assignment target, which writes rather than reads. */
    public Optional<Variable> find(String name) {
        return Optional.ofNullable(byCanonicalName.get(Keywords.canonical(name)));
    }

    /**
     * Variables nothing ever read, in declaration order. A declared-and-never-read variable is
     * almost always a rename left half-finished, so it is rejected rather than tolerated.
     */
    public List<Variable> neverRead() {
        return byCanonicalName.entrySet().stream()
                .filter(entry -> !read.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .toList();
    }

    public List<Variable> declared() {
        return List.copyOf(byCanonicalName.values());
    }

    private static BubasException error(LogicalLine line, Token name, String message) {
        return new BubasException(message + " (at " + name.line() + ":" + name.column() + ")",
                line.line(), line.source());
    }
}
