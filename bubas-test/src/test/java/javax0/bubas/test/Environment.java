package javax0.bubas.test;

import javax0.bubas.analyser.BubasLanguage;
import javax0.bubas.api.BubasDescribes;
import javax0.bubas.api.BubasDescription;
import javax0.bubas.api.Context;
import javax0.bubas.api.ExpressionArg;
import javax0.bubas.api.StatementContext;
import javax0.bubas.api.Value;
import javax0.bubas.support.Standard;

/**
 * The language every script in the corpus is compiled against.
 * <p>
 * Deliberately small but complete enough to reach every part of the stack: the standard statements
 * and functions, an opaque type so scripts can exercise values BUBAS cannot inspect, and
 * {@code ASSERT} so a script can check itself rather than needing the harness to read its variables.
 */
final class Environment {

    private Environment() {
    }

    /**
     * The description of {@link Parcel}, on an interface of its own.
     * <p>
     * A domain class is usually shared and has no reason to carry BUBAS annotations. Here it makes
     * no difference — {@code Parcel} is a test fixture — but the sample export is what a reader
     * will copy, so it shows the arrangement they should copy.
     */
    @BubasDescribes(Parcel.class)
    @BubasDescription("""
            Something wrapped for posting, holding one whole number.
            A script can hold one and pass it about; ask what is inside it with CONTENTS.
            """)
    public interface ParcelDoc {
    }

    /** A value BUBAS can hold and pass but never look into. */
    public static final class Parcel {
        private final long content;

        Parcel(long content) {
            this.content = content;
        }

        long content() {
            return content;
        }
    }

    @BubasDescription("""
            Wraps a whole number into a parcel.
            """)
    public static final class Wrap {
        public Parcel call(Context ctx, long value) {
            return new Parcel(value);
        }
    }

    @BubasDescription("""
            The number inside a parcel.
            """)
    public static final class Contents {
        public long call(Context ctx, Parcel parcel) {
            return parcel.content();
        }
    }

    /** Variadic: BUBAS sees CONCAT(parts STRING...) -> STRING and calls it with a spread list. */
    @BubasDescription("""
            Joins any number of pieces of text into one, in the order given.
            """)
    public static final class Concat {
        public String call(Context ctx, String... parts) {
            return String.join("", parts);
        }
    }

    /**
     * A wildcard parameter: BUBAS sees SHOW(value ANY) -> STRING and accepts any type. The handler
     * asks the value what it is rather than being told by its own signature.
     */
    @BubasDescription("""
            Renders any value as text, with the name of its type in front of it.
            """)
    public static final class Show {
        public String call(Context ctx, Value value) {
            return value.type() + "=" + String.valueOf(value.as(Object.class));
        }
    }

    @BubasDescription("""
            Writes a line to the log. Answers nothing, so it is written as a statement.
            """)
    public static final class Print {
        public void call(Context ctx, String message) {
            ctx.log("INFO", message);
        }
    }

    /**
     * {@code ASSERT "the total is right", total = 60}
     * <p>
     * The condition arrives unevaluated and is evaluated once, which is also the simplest
     * demonstration that a command controls when its expressions run.
     */
    @BubasDescription("""
            Checks that something is true, and stops the program naming the message if it is not.
            Written as a statement so a test reads as a list of claims.
            """)
    public static final class Assert {
        public void call(StatementContext ctx, String message, ExpressionArg condition) {
            if (!condition.evaluate().asBoolean()) {
                ctx.error("assertion failed: " + message);
            }
        }
    }

    /**
     * Hands back exactly what it was given, and is deliberately <em>not</em> {@code @BubasMemoizable}.
     * <p>
     * It exists so a script can hold a value the compiler may not read off the page. A condition the
     * compiler can answer is dead code and rejected, so a corpus script demonstrating a branch, a
     * loop that runs no times, or a division by zero at run time needs one value that arrives from
     * outside the text. A program parameter is how a real program does this; the corpus has none, so
     * it has this.
     */
    @BubasDescription("""
            Hands back the number it was given, unchanged.
            A value that arrives through it is not one the compiler can read off the page.
            """)
    public static final class Mirror {
        public long call(Context ctx, long value) {
            return value;
        }
    }

    /** {@link Mirror} for text. Not {@code @BubasMemoizable}, and for the same reason. */
    @BubasDescription("""
            Hands back the text it was given, unchanged.
            A value that arrives through it is not one the compiler can read off the page.
            """)
    public static final class MirrorText {
        public String call(Context ctx, String value) {
            return value;
        }
    }

    static final String ASSERT_PATTERN =
            "ASSERT {literal/STRING:message}, {expression/BOOLEAN:condition}";

    static BubasLanguage language() {
        final var builder = BubasLanguage.builder()
                .defineOpaqueTypeVia("Parcel", ParcelDoc.class)
                .defineFunction("WRAP", Wrap.class)
                .defineFunction("CONTENTS", Contents.class)
                .defineFunction("CONCAT", Concat.class)
                .defineFunction("SHOW", Show.class)
                .defineFunction("PRINT", Print.class)
                .defineFunction("MIRROR", Mirror.class)
                .defineFunction("MIRROR_TEXT", MirrorText.class)
                .defineStatement(ASSERT_PATTERN, Assert.class)
                .install(Standard::register);
        return builder.seal();
    }
}
