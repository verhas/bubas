/**
 * Turns a sealed language into a description of itself, for a reader who is not looking at the
 * Java: a code generator being told what a vocabulary is for, a subject matter expert choosing an
 * operation, anyone reading a language they did not write.
 * <p>
 * Its own module, and nothing depends on it. An export is a build-time artefact — you generate the
 * vocabulary, feed it to whatever needs it, and the running server has no use for the machinery.
 * Keeping it out of every other module is what makes shipping it a deliberate choice rather than
 * something that happens because it was on the classpath.
 */
module bubas.export {
    requires bubas.api;
    requires bubas.analyser;
    exports javax0.bubas.export;
}
