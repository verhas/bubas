package javax0.bubas.analyser.pattern;

/** One piece of a pattern: either a fixed token or a hole. */
public sealed interface PatternElement permits Literal, Placeholder {
}
