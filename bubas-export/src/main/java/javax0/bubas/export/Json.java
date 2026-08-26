package javax0.bubas.export;

import java.util.List;
import java.util.function.Consumer;

/**
 * The export as JSON, written by hand.
 * <p>
 * A serialisation library would be a dependency for four record shapes that will never gain a
 * fifth without this file being edited anyway, and this module is one an embedder may put on a
 * build classpath. Nothing here is general: it writes this model and no other.
 */
final class Json {

    private Json() {
    }

    static String of(VocabularyExport export) {
        final var out = new StringBuilder("{\n");
        array(out, "types", export.types(), type -> {
            out.append("      \"name\": ").append(quote(type.name())).append(",\n");
            out.append("      \"description\": ").append(quote(type.description())).append('\n');
        });
        out.append(",\n");
        array(out, "functions", export.functions(), function -> {
            out.append("      \"name\": ").append(quote(function.name())).append(",\n");
            out.append("      \"parameters\": [");
            for (int i = 0; i < function.parameters().size(); i++) {
                final var parameter = function.parameters().get(i);
                out.append(i == 0 ? "\n" : ",\n")
                        .append("        {\"name\": ").append(quote(parameter.name()))
                        .append(", \"type\": ").append(quote(parameter.type())).append('}');
            }
            out.append(function.parameters().isEmpty() ? "]" : "\n      ]").append(",\n");
            out.append("      \"returns\": ").append(quote(function.returns())).append(",\n");
            out.append("      \"variadic\": ").append(function.variadic()).append(",\n");
            out.append("      \"description\": ").append(quote(function.description())).append('\n');
        });
        out.append(",\n");
        array(out, "commands", export.commands(), command -> {
            out.append("      \"name\": ").append(quote(command.name())).append(",\n");
            out.append("      \"syntax\": ").append(quote(command.syntax())).append(",\n");
            out.append("      \"slots\": [");
            for (int i = 0; i < command.slots().size(); i++) {
                final var slot = command.slots().get(i);
                out.append(i == 0 ? "\n" : ",\n")
                        .append("        {\"name\": ").append(quote(slot.name()))
                        .append(", \"kind\": ").append(quote(slot.kind()))
                        .append(", \"type\": ").append(quote(slot.type()))
                        .append(", \"written\": ").append(slot.written()).append('}');
            }
            out.append(command.slots().isEmpty() ? "]" : "\n      ]").append(",\n");
            out.append("      \"description\": ").append(quote(command.description())).append('\n');
        });
        return out.append("\n}\n").toString();
    }

    private static <T> void array(StringBuilder out, String name, List<T> items,
                                  Consumer<T> body) {
        out.append("  \"").append(name).append("\": [");
        for (int i = 0; i < items.size(); i++) {
            out.append(i == 0 ? "\n" : ",\n").append("    {\n");
            body.accept(items.get(i));
            out.append("    }");
        }
        out.append(items.isEmpty() ? "]" : "\n  ]");
    }

    /** Escapes what JSON requires, and the control characters people forget until one appears. */
    private static String quote(String text) {
        final var out = new StringBuilder("\"");
        for (int i = 0; i < text.length(); i++) {
            final var c = text.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20) {
                        out.append("\\u%04X".formatted((int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
