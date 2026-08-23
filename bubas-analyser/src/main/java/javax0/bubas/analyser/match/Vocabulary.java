package javax0.bubas.analyser.match;

import javax0.bubas.analyser.Keywords;
import javax0.bubas.analyser.pattern.StatementPattern;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * What the sealed language knows about a word. The matcher needs it to tell a variable name from
 * something reserved, and to know where an expression ends.
 * <p>
 * Every question is answered case-insensitively, because reservation is case-insensitive. Whether
 * a reference is *spelled* as registered is a separate question, checked later by the analyser,
 * which can say "declared as 'userId'" rather than merely "unknown".
 */
public final class Vocabulary {

    private final Set<String> reserved;
    private final Set<String> types;
    private final Set<String> functions;

    private Vocabulary(Set<String> reserved, Set<String> types, Set<String> functions) {
        this.reserved = Set.copyOf(reserved);
        this.types = Set.copyOf(types);
        this.functions = Set.copyOf(functions);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** True for a core keyword, a pattern literal, a function name or an opaque type name. */
    public boolean isReserved(String word) {
        return reserved.contains(Keywords.canonical(word));
    }

    /** True for a built-in scalar type or a registered opaque type. */
    public boolean isTypeName(String word) {
        return types.contains(Keywords.canonical(word));
    }

    /** True for a name a script may declare or refer to as a variable. */
    public boolean isAvailableName(String word) {
        return !isReserved(word);
    }

    /**
     * True for a word that may appear inside an expression: a function name, one of the core
     * expression words, or any name the script is free to use. Everything else ends an expression,
     * which is what makes the boundary decidable without backtracking.
     */
    public boolean isExpressionWord(String word) {
        return functions.contains(Keywords.canonical(word))
                || Keywords.isExpressionWord(word)
                || isAvailableName(word);
    }

    public static final class Builder {
        private final Set<String> reserved = new HashSet<>(Keywords.CORE);
        private final Set<String> types = new HashSet<>(Keywords.SCALAR_TYPES);
        private final Set<String> functions = new HashSet<>();

        private Builder() {
        }

        public Builder function(String... names) {
            for (final var name : names) {
                functions.add(Keywords.canonical(name));
                reserved.add(Keywords.canonical(name));
            }
            return this;
        }

        public Builder opaqueType(String... names) {
            for (final var name : names) {
                types.add(Keywords.canonical(name));
                reserved.add(Keywords.canonical(name));
            }
            return this;
        }

        public Builder patterns(Collection<StatementPattern> patterns) {
            patterns.forEach(p -> reserved.addAll(p.reservedWords()));
            return this;
        }

        public Vocabulary build() {
            return new Vocabulary(reserved, types, functions);
        }
    }
}
