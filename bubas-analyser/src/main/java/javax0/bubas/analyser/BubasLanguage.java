package javax0.bubas.analyser;

import javax0.bubas.analyser.core.Lowering;
import javax0.bubas.analyser.flow.FlowAnalyser;
import javax0.bubas.analyser.match.OverlapAnalysis;
import javax0.bubas.analyser.match.Vocabulary;
import javax0.bubas.analyser.pattern.ConstraintResolver;
import javax0.bubas.analyser.pattern.PatternParser;
import javax0.bubas.analyser.pattern.StatementPattern;
import javax0.bubas.analyser.statement.StatementParser;
import javax0.bubas.api.BubasDefinitionException;
import javax0.bubas.api.BubasType;
import javax0.bubas.api.Registrar;
import javax0.bubas.lexer.Lexer;

import java.util.*;
import java.util.function.Consumer;

/**
 * A sealed vocabulary: the opaque types, functions and statements a script may use.
 * <p>
 * Registration happens on a {@link Builder} and closes at {@link Builder#seal()}. It has to close,
 * because from that moment the reserved-word set is fixed — a later registration could reserve a
 * word an already-compiled program used as a variable name. Sealing is also the only point at which
 * some checks are even possible: whether two patterns can match the same line depends on what every
 * other pattern reserves.
 * <p>
 * A sealed language is immutable and safe to share across threads. It is deliberately expensive to
 * build and cheap to reuse.
 */
public final class BubasLanguage {

    private final Vocabulary vocabulary;
    private final ConstraintResolver constraints;
    private final Map<String, BubasType.Opaque> opaqueTypes;
    private final Map<String, FunctionSignature> functions;
    private final List<CommandDefinition> commands;
    private final Map<Class<?>, Map<String, Object>> services;

    private BubasLanguage(Vocabulary vocabulary, ConstraintResolver constraints,
                          Map<String, BubasType.Opaque> opaqueTypes,
                          Map<String, FunctionSignature> functions, List<CommandDefinition> commands,
                          Map<Class<?>, Map<String, Object>> services) {
        this.vocabulary = vocabulary;
        this.constraints = constraints;
        // Ordered rather than Map.copyOf: registration order is what enumeration reports, and a
        // vocabulary listing is far easier to read in the order the embedder wrote it.
        this.opaqueTypes = Collections.unmodifiableMap(new LinkedHashMap<>(opaqueTypes));
        this.functions = Collections.unmodifiableMap(new LinkedHashMap<>(functions));
        this.commands = List.copyOf(commands);
        final var frozen = new LinkedHashMap<Class<?>, Map<String, Object>>();
        services.forEach((type, byQualifier) -> frozen.put(type, Map.copyOf(byQualifier)));
        this.services = Map.copyOf(frozen);
    }

    /**
     * Services every interpreter of this language starts with, keyed by type and then qualifier.
     * <p>
     * A language is sealed once and shared, so anything registered here is one object serving every
     * run — including runs on different threads at the same time. It must be thread-safe. Anything
     * that varies per run, or that is not safe to share, belongs on the interpreter instead.
     */
    public Map<Class<?>, Map<String, Object>> services() {
        return services;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Vocabulary vocabulary() {
        return vocabulary;
    }

    /**
     * Resolves a pattern constraint against the types this language registered.
     */
    public ConstraintResolver constraints() {
        return constraints;
    }

    public Optional<BubasType> opaqueType(String name) {
        return Optional.ofNullable(opaqueTypes.get(Keywords.canonical(name)));
    }

    public Optional<FunctionSignature> function(String name) {
        return Optional.ofNullable(functions.get(Keywords.canonical(name)));
    }

    /**
     * Every registered function, in registration order.
     * <p>
     * Lookup by name answers "is this word a function"; this answers "what is in this language",
     * which is what a test framework, a vocabulary export or a prompt generator needs.
     */
    public List<FunctionSignature> functions() {
        return List.copyOf(functions.values());
    }

    /** Every registered opaque type, in registration order. */
    public List<BubasType.Opaque> opaqueTypes() {
        return List.copyOf(opaqueTypes.values());
    }

    public List<CommandDefinition> commands() {
        return commands;
    }

    /**
     * Lexes, parses and checks one source.
     * <p>
     * The result is reusable across runs, which is the point of separating this from execution: a
     * script compiled once can be run many times, each run getting nothing but a fresh variable
     * store.
     *
     * @throws javax0.bubas.api.BubasException on the first problem found
     */
    public BubasProgram compile(String source) {
        final var program = StatementParser.parse(Lexer.lex(source), this);
        final var symbols = FlowAnalyser.check(program, this);
        return new BubasProgram(this, Lowering.lower(program, this, symbols));
    }

    public static final class Builder implements Registrar {

        private final Map<String, Class<?>> opaqueTypes = new LinkedHashMap<>();
        private final Map<String, Class<?>> functions = new LinkedHashMap<>();
        private final Map<String, Class<?>> statements = new LinkedHashMap<>();
        private final Map<Class<?>, Map<String, Object>> services = new LinkedHashMap<>();
        private boolean skipOverlapAnalysis;

        private Builder() {
        }

        @Override
        public Builder defineOpaqueType(String name, Class<?> javaType) {
            opaqueTypes.put(name, javaType);
            return this;
        }

        @Override
        public Builder defineFunction(String name, Class<?> implementation) {
            functions.put(name, implementation);
            return this;
        }

        @Override
        public Builder defineStatement(String pattern, Class<?> implementation) {
            statements.put(pattern, implementation);
            return this;
        }

        @Override
        public Builder defineOpaqueTypes(Map<String, Class<?>> javaTypes) {
            javaTypes.forEach(this::defineOpaqueType);
            return this;
        }

        @Override
        public Builder defineFunctions(Map<String, Class<?>> implementations) {
            implementations.forEach(this::defineFunction);
            return this;
        }

        @Override
        public Builder defineStatements(Map<String, Class<?>> implementations) {
            implementations.forEach(this::defineStatement);
            return this;
        }

        @Override
        public Builder install(Consumer<Registrar> installer) {
            installer.accept(this);
            return this;
        }

        /**
         * Registers a service shared by every interpreter this language produces, which is what
         * makes a singleton a singleton rather than something each run has to be handed again.
         * <p>
         * One object serves every run, concurrent runs included, so it must be thread-safe. An
         * interpreter may register its own service of the same type and qualifier, and that one
         * wins for that run.
         */
        public <T> Builder registerService(Class<T> type, T service) {
            return registerService(type, "", service);
        }

        /** For the rare case of two services of one type: a read replica and a primary, say. */
        public <T> Builder registerService(Class<T> type, String qualifier, T service) {
            services.computeIfAbsent(type, ignored -> new LinkedHashMap<>()).put(qualifier, service);
            return this;
        }

        /**
         * Overlap analysis is conservative, so it can reject a pair that would never collide. Skip
         * it for startup cost in production, or for a grammar whose author knows better — not
         * because it complained once.
         */
        public Builder skipOverlapAnalysis(boolean skip) {
            skipOverlapAnalysis = skip;
            return this;
        }

        public BubasLanguage seal() {
            final var types = opaqueTypes();
            final var patterns = statements.keySet().stream().map(PatternParser::parse).toList();
            checkNamesAreDistinct(patterns);
            checkPlaceholdersAreNotTypeNames(patterns, types);
            final var constraints = new ConstraintResolver(types);
            constraints.check(patterns);

            final var javaTypes = new JavaTypes(byJavaClass(types));
            final var signatures = new LinkedHashMap<String, FunctionSignature>();
            functions.forEach((name, implementation) -> signatures.put(Keywords.canonical(name),
                    FunctionSignature.derive(name, Implementation.of(implementation,
                            "function " + name), javaTypes)));

            final var definitions = new ArrayList<CommandDefinition>();
            for (final var pattern : patterns) {
                final var where = "command \"" + pattern + "\"";
                definitions.add(CommandDefinition.of(pattern,
                        Implementation.of(statements.get(pattern.source()), where), constraints));
            }

            checkDeclaredNamesAreDistinct(definitions);

            final var vocabulary = Vocabulary.builder()
                    .function(functions.keySet().toArray(String[]::new))
                    .opaqueType(opaqueTypes.keySet().toArray(String[]::new))
                    .patterns(patterns)
                    .build();
            if (!skipOverlapAnalysis) {
                new OverlapAnalysis(vocabulary).check(patterns);
            }
            return new BubasLanguage(vocabulary, constraints, types, signatures, definitions,
                    services);
        }

        /**
         * Two commands may not carry names differing only in case, for the reason two variables may
         * not: a reader cannot tell them apart, and whoever wrote the second meant the first.
         * <p>
         * Only annotated names are checked. Skeletons are not: an application that never mocks
         * anything should not be made to invent names for commands nobody will ever refer to.
         */
        private static void checkDeclaredNamesAreDistinct(List<CommandDefinition> definitions) {
            final var byCanonical = new LinkedHashMap<String, CommandDefinition>();
            for (final var definition : definitions) {
                if (!definition.isNamed()) {
                    continue;
                }
                final var previous = byCanonical.put(
                        Keywords.canonical(definition.name()), definition);
                if (previous != null) {
                    throw new BubasDefinitionException("two commands are named '"
                            + previous.name() + "' and '" + definition.name()
                            + "'; names are unique ignoring case:\n        " + previous.pattern()
                            + "\n        " + definition.pattern());
                }
            }
        }

        /**
         * Registered types, keyed canonically. The class-to-type mapping must stay one-to-one.
         */
        private Map<String, BubasType.Opaque> opaqueTypes() {
            final var byName = new LinkedHashMap<String, BubasType.Opaque>();
            final var claimedBy = new HashMap<Class<?>, String>();
            opaqueTypes.forEach((name, javaType) -> {
                final var previous = claimedBy.put(javaType, name);
                if (previous != null) {
                    throw new BubasDefinitionException("opaque types '" + previous + "' and '" + name
                            + "' are both registered against " + javaType.getTypeName()
                            + "; a Java class identifies one BUBAS type, so the mapping must be "
                            + "one-to-one for a signature to be derivable");
                }
                byName.put(Keywords.canonical(name),
                        (BubasType.Opaque) BubasType.opaque(name, javaType));
            });
            return byName;
        }

        private static Map<Class<?>, BubasType.Opaque> byJavaClass(
                Map<String, BubasType.Opaque> types) {
            final var byClass = new HashMap<Class<?>, BubasType.Opaque>();
            types.values().forEach(type -> byClass.put(type.javaType(), type));
            return byClass;
        }

        /**
         * Names live in one namespace. Pattern literals may repeat and may be core keywords — a
         * command reading {@code SELECT 2 FROM a AND b} wants {@code AND} — but a function or type
         * name may collide with nothing at all.
         */
        private void checkNamesAreDistinct(List<StatementPattern> patterns) {
            final var reservedByPatterns = new HashMap<String, String>();
            patterns.forEach(pattern -> pattern.reservedWords()
                    .forEach(word -> reservedByPatterns.put(word, pattern.source())));
            final var claimed = new HashMap<String, String>();
            opaqueTypes.keySet().forEach(name -> claim(claimed, reservedByPatterns, name,
                    "opaque type '" + name + "'"));
            functions.keySet().forEach(name -> claim(claimed, reservedByPatterns, name,
                    "function '" + name + "'"));
        }

        private static void claim(Map<String, String> claimed, Map<String, String> byPatterns,
                                  String name, String what) {
            final var canonical = Keywords.canonical(name);
            if (Keywords.CORE.contains(canonical)) {
                throw new BubasDefinitionException(what + " collides with the core keyword "
                        + canonical);
            }
            final var pattern = byPatterns.get(canonical);
            if (pattern != null) {
                throw new BubasDefinitionException(what + " collides with a keyword of the pattern \""
                        + pattern + "\"");
            }
            final var previous = claimed.put(canonical, what);
            if (previous != null) {
                throw new BubasDefinitionException(what + " collides with " + previous
                        + "; names are unique case-insensitively");
            }
        }

        /**
         * A placeholder may not be named after a type, so that a constraint {@code /X} has exactly
         * one reading: the placeholder when the pattern has one by that name, the type otherwise.
         */
        private static void checkPlaceholdersAreNotTypeNames(List<StatementPattern> patterns,
                                                             Map<String, BubasType.Opaque> types) {
            for (final var pattern : patterns) {
                for (final var placeholder : pattern.placeholders()) {
                    final var canonical = Keywords.canonical(placeholder.name());
                    if (Keywords.SCALAR_TYPES.contains(canonical) || types.containsKey(canonical)) {
                        throw new BubasDefinitionException("in pattern \"" + pattern
                                + "\": placeholder '" + placeholder.name()
                                + "' is named after a type, so a constraint referring to it could "
                                + "mean either the placeholder or the type");
                    }
                }
            }
        }
    }
}
