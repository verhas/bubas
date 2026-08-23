package javax0.bubas.analyser.pattern;

import java.util.Set;

/**
 * A hole in a pattern.
 *
 * @param kind           what it captures
 * @param name           its name; an unnamed placeholder takes its kind's spelling
 * @param constraint     the type constraint as written, or {@code null}
 * @param preconditions  what it requires of the variable, empty unless {@link Kind#VAR}
 * @param postconditions what it guarantees about the variable, empty unless {@link Kind#VAR}
 */
public record Placeholder(Kind kind, String name, Constraint constraint,
                          Set<Precondition> preconditions,
                          Set<Postcondition> postconditions) implements PatternElement {

    /**
     * True when this placeholder brings a variable into existence: it says {@code new}, or implies
     * it by making one {@code final}. Only an {@link Kind#IDENTIFIER} can, since {@code a[i]} is
     * not a name.
     */
    public boolean creates() {
        return preconditions.contains(Precondition.NEW)
                || postconditions.contains(Postcondition.FINAL);
    }
}
