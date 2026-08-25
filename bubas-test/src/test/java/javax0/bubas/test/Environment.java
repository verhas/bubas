package javax0.bubas.test;

import javax0.bubas.analyser.BubasLanguage;
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

    public static final class Wrap {
        public Parcel call(Context ctx, long value) {
            return new Parcel(value);
        }
    }

    public static final class Contents {
        public long call(Context ctx, Parcel parcel) {
            return parcel.content();
        }
    }

    /** Variadic: BUBAS sees CONCAT(parts STRING...) -> STRING and calls it with a spread list. */
    public static final class Concat {
        public String call(Context ctx, String... parts) {
            return String.join("", parts);
        }
    }

    /**
     * A wildcard parameter: BUBAS sees SHOW(value ANY) -> STRING and accepts any type. The handler
     * asks the value what it is rather than being told by its own signature.
     */
    public static final class Show {
        public String call(Context ctx, Value value) {
            return value.type() + "=" + String.valueOf(value.as(Object.class));
        }
    }

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
    public static final class Assert {
        public void call(StatementContext ctx, String message, ExpressionArg condition) {
            if (!condition.evaluate().asBoolean()) {
                ctx.error("assertion failed: " + message);
            }
        }
    }

    static final String ASSERT_PATTERN =
            "ASSERT {literal/STRING:message}, {expression/BOOLEAN:condition}";

    static BubasLanguage language() {
        final var builder = BubasLanguage.builder()
                .defineOpaqueType("Parcel", Parcel.class)
                .defineFunction("WRAP", Wrap.class)
                .defineFunction("CONTENTS", Contents.class)
                .defineFunction("CONCAT", Concat.class)
                .defineFunction("SHOW", Show.class)
                .defineFunction("PRINT", Print.class)
                .defineStatement(ASSERT_PATTERN, Assert.class)
                .install(Standard::register);
        return builder.seal();
    }
}
