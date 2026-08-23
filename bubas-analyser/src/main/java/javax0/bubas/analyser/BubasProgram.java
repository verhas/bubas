package javax0.bubas.analyser;

import javax0.bubas.analyser.core.CoreProgram;
import javax0.bubas.api.BubasType;

import java.util.List;

/**
 * One source, parsed and fully checked.
 * <p>
 * Immutable and reusable: compiling is expensive and running is not, so a program is compiled once
 * and executed as often as wanted, by one {@code Interpreter} per run. Nothing here is resolved by
 * name — every call in the tree already carries its signature and every command its implementation
 * — so execution never consults a registry.
 */
public final class BubasProgram {

    private final BubasLanguage language;
    private final CoreProgram core;

    BubasProgram(BubasLanguage language, CoreProgram core) {
        this.language = language;
        this.core = core;
    }

    /** Documentation, and not otherwise significant. */
    public String name() {
        return core.name();
    }

    /** How many of the leading slots the embedder supplies. They are final and already assigned. */
    public int parameterCount() {
        return core.parameters();
    }

    /** {@code null} when the program declares no {@code RETURNS}. */
    public BubasType returns() {
        return core.returns();
    }

    /** Every variable, in slot order, parameters first. */
    public List<CoreProgram.Slot> variables() {
        return core.slots();
    }

    /** What a back end executes or emits. */
    public CoreProgram core() {
        return core;
    }

    /** The language this was compiled against, which carries the services a run needs. */
    public BubasLanguage language() {
        return language;
    }

    @Override
    public String toString() {
        return "program " + name();
    }
}
