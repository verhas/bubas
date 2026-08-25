package javax0.bubas.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Names a command, replacing the skeleton derived from its pattern.
 * <p>
 * A command is ordinarily referred to — by a test framework, a vocabulary listing, a diagnostic —
 * by its <em>skeleton</em>: the pattern's literals verbatim with each placeholder written {@code _},
 * so {@code VALIDATE {…:item} AGAINST {…:rules}} is {@code VALIDATE _ AGAINST _}. That name is
 * derived, so it needs no decision and cannot drift from the pattern.
 * <p>
 * This annotation is the deliberate alternative, for a team that would rather write
 * {@code "LoanValidation"} than a skeleton, and the escape route when two patterns are distinct
 * enough for the language yet share a skeleton. It <strong>replaces</strong> the skeleton: once a
 * command is named, its skeleton no longer refers to it, because two ways to name one thing is how
 * two halves of a test suite end up written in different dialects.
 * <p>
 * The name must be non-blank and contain no whitespace, and no two annotated commands in one
 * language may have names differing only in case. Unannotated commands are not checked against each
 * other: an application that never mocks anything should not be made to invent names.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface BubasCommandName {
    String value();
}
