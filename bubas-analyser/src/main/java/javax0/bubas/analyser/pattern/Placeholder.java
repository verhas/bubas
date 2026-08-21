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
}
