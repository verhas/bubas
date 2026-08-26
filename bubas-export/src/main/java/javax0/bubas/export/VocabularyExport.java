package javax0.bubas.export;

import javax0.bubas.analyser.BubasLanguage;
import javax0.bubas.analyser.CommandDefinition;
import javax0.bubas.analyser.FunctionSignature;
import javax0.bubas.api.BubasDefinitionException;
import javax0.bubas.api.BubasDescription;
import javax0.bubas.api.BubasReviewed;
import javax0.bubas.api.Surface;
import javax0.bubas.analyser.pattern.Constraint;
import javax0.bubas.analyser.pattern.Placeholder;
import javax0.bubas.analyser.pattern.Postcondition;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A sealed language, described.
 * <p>
 * Everything here is either derived from the language or written as a {@link BubasDescription}, and
 * those two halves do not overlap: shape is derived, meaning is written. That is why a description
 * must never restate a signature — the signature is already beside it, and the day the two disagree
 * the prose is the one that is wrong.
 * <p>
 * <strong>Java is not in the export.</strong> No class names, no packages, no implementation
 * detail. A reader of this is going to write BUBAS, and the Java behind a function is exactly as
 * relevant to them as it is to a script: not at all. It also means an export can be handed to
 * someone without handing them an inventory of the host application's internals.
 * <p>
 * <strong>Descriptions are required here and nowhere else.</strong> A language without them seals,
 * compiles and runs perfectly; it simply cannot be exported, because an export with holes in it is
 * worse than no export — it reads like documentation. Putting the requirement on the export rather
 * than on the builder means nobody who does not export pays for it, and nobody who does can forget.
 */
public final class VocabularyExport {

    /** An opaque type: a value a script may hold and pass, and never look inside. */
    public record Type(String name, String description) {
    }

    public record Parameter(String name, String type) {
    }

    public record Function(String name, List<Parameter> parameters, String returns,
                           boolean variadic, String description) {
    }

    /**
     * One placeholder of a command's pattern.
     *
     * @param kind    what may be written there — an expression, a variable, a literal, a type name
     * @param type    the type it is constrained to, or {@code any}
     * @param written whether the command leaves a value in it, which is what makes a statement a
     *                declaration rather than merely a use
     */
    public record Slot(String name, String kind, String type, boolean written) {
    }

    /**
     * @param name    what a tool refers to it by: the pattern's skeleton, or the name it was given
     * @param syntax  the pattern as registered, whose notation is [SPEC §9.2]
     */
    public record Command(String name, String syntax, List<Slot> slots, String description) {
    }

    private final List<Type> types;
    private final List<Function> functions;
    private final List<Command> commands;

    private VocabularyExport(List<Type> types, List<Function> functions, List<Command> commands) {
        this.types = List.copyOf(types);
        this.functions = List.copyOf(functions);
        this.commands = List.copyOf(commands);
    }

    /**
     * @throws BubasDefinitionException listing everything that carries no description, all of them
     *                                  at once — an author filling holes one rebuild at a time
     *                                  gives up before the third
     */
    public static VocabularyExport of(BubasLanguage language) {
        final var missing = new ArrayList<String>();
        final var types = new ArrayList<Type>();
        for (final var type : language.opaqueTypes()) {
            final var documentation = language.documentation(type.name()).orElse(null);
            types.add(new Type(type.name(),
                    description(documentation, "opaque type " + type.name(), missing)));
        }
        final var functions = new ArrayList<Function>();
        language.functions().forEach(signature ->
                functions.add(function(signature, missing)));
        final var commands = new ArrayList<Command>();
        language.commands().forEach(definition -> commands.add(command(definition, missing)));
        if (!missing.isEmpty()) {
            throw new BubasDefinitionException("nothing describes:\n        "
                    + String.join("\n        ", missing)
                    + "\n    An export says what a vocabulary means, so it cannot be built out of"
                    + " things nobody has said anything about. Add @BubasDescription to each.");
        }
        // After descriptions are known to exist: a checksum records that one was *reviewed*, so it
        // means nothing where there is none to review.
        reviewed(language);
        return new VocabularyExport(types, functions, commands);
    }

    public List<Type> types() {
        return types;
    }

    public List<Function> functions() {
        return functions;
    }

    public List<Command> commands() {
        return commands;
    }

    /** For a tool. */
    public String asJson() {
        return Json.of(this);
    }

    /** For a prompt, or a person. */
    public String asMarkdown() {
        return Markdown.of(this);
    }

    /**
     * Refuses an export whose descriptions were reviewed against a different shape.
     * <p>
     * Here rather than at {@code seal()}, which is where it was first built and where it was wrong.
     * A checksum fires on any change to a described class's public surface — most of which are
     * ordinary development, unrelated to documentation. Failing to seal would break startup and
     * every test of an application that generates no documentation at all, and the rational answer
     * to that is to delete the annotation. A check that fires too widely destroys the thing it
     * protects; this one now reaches only the person generating documentation, who is the person
     * it is asking.
     * <p>
     * Only classes carrying {@link javax0.bubas.api.BubasReviewed} are checked, so reviewing stays
     * opt-in per class. An empty value is the first time: the checksum is reported and nobody is
     * asked to review anything, because there is nothing yet to compare against.
     * <p>
     * The checksum is reported, never written. A build that edits its own sources to make itself
     * pass has stopped being a check.
     */
    private static void reviewed(BubasLanguage language) {
        for (final var type : language.opaqueTypes()) {
            review(type.javaType(),
                    language.documentation(type.name()).orElse(type.javaType()));
        }
        language.functions().forEach(signature ->
                review(signature.implementation().owner(), signature.implementation().owner()));
        language.commands().forEach(definition ->
                review(definition.implementation().owner(), definition.implementation().owner()));
    }

    private static void review(Class<?> subject, Class<?> documentation) {
        final var reviewed = documentation.getAnnotation(BubasReviewed.class);
        if (reviewed == null) {
            return;
        }
        final var current = Surface.checksum(subject);
        if (current.equals(reviewed.value())) {
            return;
        }
        if (reviewed.value().isEmpty()) {
            throw new BubasDefinitionException("write " + current + " into @BubasReviewed on "
                    + documentation.getTypeName());
        }
        throw new BubasDefinitionException(subject.getTypeName() + " has changed since its"
                + " description was reviewed. Its public surface is now:\n        "
                + String.join("\n        ", Surface.of(subject))
                + "\n    Re-read the description, then write " + current
                + " into @BubasReviewed on " + documentation.getTypeName());
    }

    private static Function function(FunctionSignature signature, List<String> missing) {
        final var parameters = new ArrayList<Parameter>();
        signature.parameters().forEach(parameter ->
                parameters.add(new Parameter(parameter.name(), parameter.type().toString())));
        return new Function(signature.name(), parameters, signature.returnType().toString(),
                signature.varargs(),
                description(signature.implementation().owner(),
                        "function " + signature.name(), missing));
    }

    private static Command command(CommandDefinition definition, List<String> missing) {
        final var slots = new ArrayList<Slot>();
        definition.pattern().placeholders().forEach(placeholder -> slots.add(slot(placeholder)));
        return new Command(definition.name(), definition.pattern().source(), slots,
                description(definition.implementation().owner(),
                        "command " + definition.name(), missing));
    }

    private static Slot slot(Placeholder placeholder) {
        return new Slot(placeholder.name(),
                placeholder.kind().name().toLowerCase(java.util.Locale.ROOT),
                constraint(placeholder.constraint()),
                placeholder.postconditions().contains(Postcondition.INITIALIZED)
                        || placeholder.postconditions().contains(Postcondition.FINAL));
    }

    private static String constraint(Constraint constraint) {
        return switch (constraint) {
            case null -> "any";
            case Constraint.Named named -> named.name();
            case Constraint.ArrayOf arrayOf -> constraint(arrayOf.element()) + "[]";
            case Constraint.ElementOf elementOf -> "element of " + elementOf.placeholder();
        };
    }

    /** Records what is undescribed rather than failing on the first, so one pass finds them all. */
    private static String description(Class<?> documentation, String what, List<String> missing) {
        return Optional.ofNullable(documentation)
                .map(owner -> owner.getAnnotation(BubasDescription.class))
                .map(BubasDescription::value)
                .map(String::strip)
                .filter(text -> !text.isEmpty())
                .orElseGet(() -> {
                    missing.add(what);
                    return null;
                });
    }
}
