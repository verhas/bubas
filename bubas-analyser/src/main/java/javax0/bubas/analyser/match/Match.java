package javax0.bubas.analyser.match;

import javax0.bubas.analyser.pattern.StatementPattern;

import java.util.Map;

/**
 * A line matched against a pattern.
 *
 * @param pattern  the pattern that matched
 * @param bindings what each placeholder captured, keyed by placeholder name
 */
public record Match(StatementPattern pattern, Map<String, Binding> bindings) {

    public Binding binding(String name) {
        final var binding = bindings.get(name);
        if (binding == null) {
            throw new IllegalArgumentException(pattern + " has no placeholder named '" + name + "'");
        }
        return binding;
    }
}
