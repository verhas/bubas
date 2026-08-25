package javax0.bubas.bunit.standard;

import javax0.bubas.analyser.BubasLanguage;
import javax0.bubas.bunit.commands.Bunit;
import javax0.bubas.bunit.matchers.Matchers;
import javax0.bubas.support.Standard;

/**
 * The language a BUBAS unit test is written in: the standard statements plus BUNIT's own.
 * <p>
 * It is a constant. Because a test names the subject's functions and commands with STRING literals
 * rather than with syntax, it needs none of the subject's vocabulary — no opaque types, no
 * functions, no patterns — so one sealed language serves every embedder and every test. Only the
 * runner needs the subject's own language, to check what the test mocks and to run the subject.
 * <p>
 * The standard statements come along because a test that computes anything wants {@code DECLARE}
 * and assignment.
 */
public final class BunitLanguage {

    private BunitLanguage() {
    }

    private static final BubasLanguage LANGUAGE = BubasLanguage.builder()
            .install(Standard::register)
            .install(Matchers::register)
            .install(Bunit::register)
            .seal();

    /** Sealed once and shared: a language is immutable, so there is nothing to hand out per test. */
    public static BubasLanguage get() {
        return LANGUAGE;
    }
}
