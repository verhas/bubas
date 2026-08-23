package javax0.bubas.analyser;

import javax0.bubas.api.BubasDefinitionException;
import javax0.bubas.api.Param;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

/**
 * An implementation class, resolved.
 * <p>
 * One class is one function or one command. The implementing method is the single public
 * <em>instance</em> method the class <em>declares</em>; its name is irrelevant and helpers are
 * private. Declaring none, or more than one, fails here rather than being silently substituted.
 * <p>
 * Static methods are excluded, which is what lets a {@code provider()} factory coexist with the
 * implementation: the class is instantiated, so a static method could never have been the method
 * being called.
 *
 * @param owner    the class, instantiated once per sealed language
 * @param method   its single public declared method
 * @param instance the instance every call goes through
 */
public record Implementation(Class<?> owner, Method method, Object instance) {

    /**
     * @param owner the implementation class
     * @param where what is being defined, for the diagnostic
     */
    public static Implementation of(Class<?> owner, String where) {
        final var candidates = Arrays.stream(owner.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> !Modifier.isStatic(m.getModifiers()))
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .filter(m -> !overridesObject(m))
                .toList();
        if (candidates.isEmpty()) {
            throw new BubasDefinitionException(where + ": " + owner.getTypeName()
                    + " declares no public instance method, so there is nothing to call");
        }
        if (candidates.size() > 1) {
            throw new BubasDefinitionException(where + ": " + owner.getTypeName()
                    + " declares " + candidates.size() + " public instance methods ("
                    + candidates.stream().map(Method::getName).sorted().reduce((a, b) -> a + ", " + b)
                    .orElse("") + "); one class is one function or one command, so helpers must be "
                    + "private");
        }
        return new Implementation(owner, candidates.getFirst(), instantiate(owner, where));
    }

    /**
     * The runtime constructs the class itself, with no arguments, so it cannot capture the
     * embedder's objects the way a lambda could. That is what makes {@code ctx.service(...)} the
     * only way to reach a dependency. A public static {@code provider()} is accepted as well,
     * deliberately the same contract {@code ServiceLoader} uses, so one class works through either
     * registration route.
     */
    private static Object instantiate(Class<?> owner, String where) {
        try {
            final var provider = Arrays.stream(owner.getDeclaredMethods())
                    .filter(m -> "provider".equals(m.getName()) && m.getParameterCount() == 0)
                    .filter(m -> Modifier.isStatic(m.getModifiers()) && Modifier.isPublic(m.getModifiers()))
                    .findFirst();
            if (provider.isPresent()) {
                return provider.get().invoke(null);
            }
            return owner.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new BubasDefinitionException(where + ": " + owner.getTypeName()
                    + " needs a public no-argument constructor or a public static provider() method",
                    e);
        }
    }

    private static boolean overridesObject(Method method) {
        try {
            Object.class.getMethod(method.getName(), method.getParameterTypes());
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    /** The parameter names, from {@code @Param} where given and the Java names otherwise. */
    public List<String> parameterNames() {
        return Arrays.stream(method.getParameters())
                .map(p -> p.isAnnotationPresent(Param.class)
                        ? p.getAnnotation(Param.class).value()
                        : p.getName())
                .toList();
    }
}
