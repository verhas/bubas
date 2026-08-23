package javax0.bubas.analyser.core;

import javax0.bubas.api.BubasType;
import javax0.bubas.lexer.Token;

/** What a custom command's placeholder hands to its implementation, resolved. */
public sealed interface CoreArgument {

    Token token();

    /**
     * A variable, by slot.
     *
     * @param index {@code null} unless the reference was indexed. It is carried unevaluated: the
     *              command decides whether to evaluate it, at most once.
     * @param name  kept only so a handler can name it in a diagnostic
     */
    record Slot(int slot, CoreExpression index, BubasType type, boolean isFinal, String name,
                Token token) implements CoreArgument {
    }

    /** An expression the command evaluates when, and as often as, it likes. */
    record Lazy(CoreExpression expression, Token token) implements CoreArgument {
    }

    /** A compile-time constant, sign already applied. */
    record Constant(Object value, BubasType type, Token token) implements CoreArgument {
    }

    record Type(BubasType type, Token token) implements CoreArgument {
    }
}
