package javax0.bubas.analyser.core;

import javax0.bubas.api.BubasType;

import java.util.List;

/**
 * A program reduced to core form: what every back end executes or emits.
 *
 * @param slots      every variable, in slot order, parameters first. Declarations are top level, so
 *                   the set is fixed before the program starts and a run needs one flat array
 * @param parameters how many of the leading slots the embedder supplies
 * @param returns    {@code null} without a {@code RETURNS} clause
 */
public record CoreProgram(String name, List<Slot> slots, int parameters, BubasType returns,
                          List<CoreStatement> body) {

    /**
     * @param isFinal a parameter or a {@code FINAL} declaration; never assigned after it is created
     */
    public record Slot(String name, BubasType type, boolean isFinal) {
    }
}
