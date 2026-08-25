/**
 * Argument lists and the matchers that judge them.
 * <p>
 * Its own module so that any BUNIT vocabulary can use it. A DSL with different statements — terser,
 * translated, shaped for one domain — still wants {@code ARGS} and still wants to say "any value
 * here", and neither should have to be rewritten to get them.
 * <p>
 * It sees the framework, for {@code Matcher}, and the API. It does not see the standard statements,
 * which is what keeps it usable by a vocabulary that replaces them.
 */
module bubas.bunit.matchers {
    requires bubas.api;
    requires transitive bubas.bunit;
    exports javax0.bubas.bunit.matchers;
}
