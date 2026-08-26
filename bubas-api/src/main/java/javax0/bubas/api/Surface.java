package javax0.bubas.api;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The public shape of a class, and a checksum of it.
 * <p>
 * What {@link BubasReviewed} is a checksum <em>of</em>: everything a caller of this class can see,
 * which is what a description of it is a description of. Public methods and public fields,
 * inherited ones included, because they are as available as declared ones.
 * <p>
 * Canonical, or it would be useless: reflection returns members in an unspecified order, so a
 * checksum over that order would differ between runs of the same code and everyone would learn to
 * ignore it. The members are rendered with fully qualified types and sorted.
 * <p>
 * It lives in the API rather than beside the check so that build tooling — an export, a linter, a
 * script the embedder writes — can compute the same value without depending on the compiler.
 */
public final class Surface {

    private Surface() {
    }

    /**
     * Every public member, one per line, sorted.
     * <p>
     * {@code Object}'s own members are excluded: they are on everything and say nothing about this
     * class. Synthetic and bridge members are excluded because the compiler invents them, so
     * including them would make the checksum depend on how the code was compiled rather than on
     * what it offers.
     */
    public static List<String> of(Class<?> type) {
        final var members = new ArrayList<String>();
        for (final var method : type.getMethods()) {
            if (method.getDeclaringClass() != Object.class
                    && !method.isSynthetic() && !method.isBridge()) {
                members.add(render(method));
            }
        }
        for (final var field : type.getFields()) {
            if (field.getDeclaringClass() != Object.class && !field.isSynthetic()) {
                members.add(render(field));
            }
        }
        members.sort(String::compareTo);
        return List.copyOf(members);
    }

    /** The checksum to record, short enough to type and long enough not to collide by accident. */
    public static String checksum(Class<?> type) {
        final var canonical = String.join("\n", of(type));
        try {
            final var digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            final var out = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                out.append("%02X".formatted(digest[i]));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required of every JVM", impossible);
        }
    }

    private static String render(Method method) {
        return (Modifier.isStatic(method.getModifiers()) ? "static " : "")
                + method.getReturnType().getTypeName() + " " + method.getName() + "("
                + Arrays.stream(method.getParameterTypes()).map(Class::getTypeName)
                .reduce((a, b) -> a + ", " + b).orElse("") + ")";
    }

    private static String render(Field field) {
        return (Modifier.isStatic(field.getModifiers()) ? "static " : "")
                + field.getType().getTypeName() + " " + field.getName();
    }
}
