package javax0.bubas.analyser.symbol;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Which variables definitely hold a value at one point in the program, and whether that point is
 * reachable at all.
 * <p>
 * This is a value: every operation returns a new one. Reachability travels with the set because the
 * merge rules need it — a branch that returned contributes nothing to what is known after the
 * {@code IF}, and treating it as "initialized nothing" would wrongly un-initialize everything the
 * other branch established.
 *
 * @param initialized names that definitely hold a value
 * @param reachable   false after {@code RETURN} or {@code EXIT}, until a join brings a live path in
 */
public record Assignment(Set<String> initialized, boolean reachable) {

    private static final Assignment UNREACHABLE = new Assignment(Set.of(), false);

    public Assignment {
        initialized = Set.copyOf(initialized);
    }

    public static Assignment start() {
        return new Assignment(Set.of(), true);
    }

    public static Assignment unreachable() {
        return UNREACHABLE;
    }

    public boolean isInitialized(String name) {
        return initialized.contains(name);
    }

    public Assignment initialize(String name) {
        if (!reachable || initialized.contains(name)) {
            return this;
        }
        final var wider = new HashSet<>(initialized);
        wider.add(name);
        return new Assignment(wider, true);
    }

    /**
     * What is known where paths rejoin: the intersection, because a variable is definitely assigned
     * only if every path assigned it. An unreachable path is skipped rather than intersected — it
     * contributed no execution, so it can rule nothing out.
     */
    public static Assignment merge(List<Assignment> paths) {
        final var live = paths.stream().filter(Assignment::reachable).toList();
        if (live.isEmpty()) {
            return UNREACHABLE;
        }
        final var common = new HashSet<>(live.getFirst().initialized());
        live.stream().skip(1).forEach(path -> common.retainAll(path.initialized()));
        return new Assignment(common, true);
    }

    public Assignment merge(Assignment other) {
        return merge(List.of(this, other));
    }
}
