package javax0.bubas.export;

import java.util.stream.Collectors;

/**
 * The export as prose, for a prompt or a person.
 * <p>
 * Ordered the way someone learning a vocabulary needs it rather than the way it was registered:
 * the values first, because every function and command is about one of them; then the functions,
 * which answer questions; then the commands, which do things.
 */
final class Markdown {

    private Markdown() {
    }

    static String of(VocabularyExport export) {
        final var out = new StringBuilder("# The vocabulary\n");
        if (!export.types().isEmpty()) {
            out.append("\n## Values\n\nA script may hold one of these, pass it and store it in an"
                    + " array. It can never look inside: every question about one is a function.\n");
            export.types().forEach(type -> out.append("\n### ").append(type.name()).append("\n\n")
                    .append(type.description().strip()).append('\n'));
        }
        if (!export.functions().isEmpty()) {
            out.append("\n## Functions\n");
            export.functions().forEach(function -> {
                out.append("\n### ").append(signature(function)).append("\n\n")
                        .append(function.description().strip()).append('\n');
            });
        }
        if (!export.commands().isEmpty()) {
            out.append("\n## Statements\n");
            export.commands().forEach(command -> {
                out.append("\n### ").append(command.name()).append("\n\n```\n")
                        .append(command.syntax()).append("\n```\n\n")
                        .append(command.description().strip()).append('\n');
                final var written = command.slots().stream().filter(VocabularyExport.Slot::written)
                        .map(VocabularyExport.Slot::name).collect(Collectors.joining(", "));
                if (!written.isEmpty()) {
                    out.append("\nLeaves a value in: ").append(written).append('\n');
                }
            });
        }
        return out.toString();
    }

    private static String signature(VocabularyExport.Function function) {
        final var parameters = function.parameters().stream()
                .map(parameter -> parameter.name() + " " + parameter.type())
                .collect(Collectors.joining(", "));
        return function.name() + "(" + parameters + (function.variadic() ? "..." : "") + ")"
                + ("VOID".equals(function.returns()) ? "" : " -> " + function.returns());
    }
}
