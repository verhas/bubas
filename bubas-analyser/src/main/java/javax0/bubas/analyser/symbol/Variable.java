package javax0.bubas.analyser.symbol;

import javax0.bubas.api.BubasType;
import javax0.bubas.lexer.Token;

/**
 * A declared variable.
 * <p>
 * Variable state is two axes, and they are split here by whether they are flow-sensitive.
 * Mutability is fixed at declaration and lives on this record; what is final is final from its
 * declaration and never becomes so later. Whether a variable holds a value depends on the path
 * taken to reach a statement, so it lives in {@link Assignment} instead.
 *
 * @param name       exactly as declared; every later reference must match it character for character
 * @param declaredAt the token the name was written at, so a collision can name both places
 */
public record Variable(String name, BubasType type, boolean isFinal, Token declaredAt) {
}
