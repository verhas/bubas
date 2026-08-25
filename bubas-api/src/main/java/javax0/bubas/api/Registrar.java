package javax0.bubas.api;

import java.util.Map;
import java.util.function.Consumer;

/**
 * The registration surface of a language builder, and the only view a bundle of definitions is
 * handed.
 * <p>
 * A bundle gets this instead of the builder itself so that it can contribute vocabulary and
 * delegate to further bundles, but cannot seal the language or switch overlap analysis off. Those
 * two are the embedder's calls: a library that made them on the embedder's behalf would be making
 * them for every other library in the same chain. The narrowing is static — a determined caller
 * can cast back — so it exists to keep an honest bundle inside its remit, not to contain a hostile
 * one.
 * <p>
 * It lives here rather than beside the builder because the libraries this interface exists for
 * depend on {@code bubas.api} alone. A bundle-authoring interface that dragged in the analyser
 * would defeat its own purpose.
 * <p>
 * Every method returns this interface, so definitions chain inside a bundle. An implementation is
 * free to override each one with a covariant return, which is what lets an embedder's own chain
 * run through these calls and still end in whatever seals the language.
 */
public interface Registrar {

    Registrar defineOpaqueType(String name, Class<?> javaType);

    Registrar defineFunction(String name, Class<?> implementation);

    Registrar defineStatement(String pattern, Class<?> implementation);

    Registrar defineOpaqueTypes(Map<String, Class<?>> javaTypes);

    Registrar defineFunctions(Map<String, Class<?>> implementations);

    Registrar defineStatements(Map<String, Class<?>> implementations);

    /**
     * Applies a bundle of definitions to this registrar, which is what keeps a vocabulary several
     * embedders share in one place instead of copied into each chain.
     */
    Registrar install(Consumer<Registrar> installer);
}
