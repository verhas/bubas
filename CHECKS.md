# BUBAS Domain Checks — Specification

**Version** 1.0 · **Status** Design agreed; not implemented. See [`SPEC.md`](SPEC.md) for the
language itself.

A domain check is a static rule an embedder adds on top of the ones BUBAS enforces for itself. It
sees a compiled program and reports what a type system cannot express: that a path literal resolves
against a schema, that loops are not nested five deep, that no command writes a variable somebody
else already owns.

This document is the contract between the check SPI and the applications that use it. Where it and
the implementation disagree, this document wins.

---

**Contents**

<!--TOC min-level: 2
max-level: 2
_content_generated_: 551:md5:7ce73da32cf30cd85ca55fe533080114
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
- [1. Scope](#1-scope)
- [2. Lifecycle](#2-lifecycle)
- [3. The check interface](#3-the-check-interface)
- [4. The program](#4-the-program)
- [5. Statements](#5-statements)
- [6. Expressions](#6-expressions)
- [7. Arguments](#7-arguments)
- [8. Derived views](#8-derived-views)
- [9. Failure](#9-failure)
- [10. Obligations on an implementation](#10-obligations-on-an-implementation)
- [11. Packaging](#11-packaging)
- [12. Interaction with BUNIT](#12-interaction-with-bunit)
- [13. Examples](#13-examples)
- [14. Open questions](#14-open-questions)
<!--/TOC-->

---

## 1. Scope

### 1.1. What a check is for

BUBAS already refuses a program that reads a variable before it is assigned, that gives a function
the wrong type, or that falls off the end without returning. Those are properties of *any* BUBAS
program and need no help.

A domain check exists for the rules that are true of *your* programs only, and that no type could
carry:

- a `STRING` literal whose content must match a shape — a dotted path, an ISO date, a currency code
- a path that must resolve against an external model — a JSON Schema, a database catalogue
- structural limits — nesting depth, expression complexity, count of variables or parameters
- house rules — which operations may write which variables, which may appear inside a loop

### 1.2. What a check is not for

**Not re-checking what the language guarantees.** By the time a check runs, argument types are
already correct, definite assignment already holds, every path already returns. A check that
re-asserts these is testing BUBAS.

**Not checking the vocabulary.** A rule about the *shape of a language* — that every `BOOLEAN`
function is named `IS_…`, that every schema field has a reader — needs no SPI at all.
`BubasLanguage` exposes `functions()`, `commands()` and `opaqueTypes()`; a plain method or a unit
test over a sealed language does this with better tooling and no framework.

> **Rationale (not normative).** The dividing line is whether the rule must be applied
> *automatically to things that do not exist yet*. A vocabulary is sealed once, so a rule about it
> can simply be run once, by hand. Programs arrive indefinitely — a rule-writer saves a new one next
> week — and a rule that has to be remembered is a rule that will be forgotten. That is the whole
> justification for the machinery in this document, and it does not extend to the vocabulary.

## 2. Lifecycle

A check is **registered** while the language is being built and **runs** once per compilation.

```java
BubasLanguage language = BubasLanguage.builder()
        .install(Standard::register)
        .defineFunction("GET_FIELD", GetField.class)
        .check(new SchemaPathCheck(schema))
        .check(new MaxLoopNestingCheck(3))
        .seal();
```

At `seal()` the registered checks are captured into the immutable `BubasLanguage`. At `compile()`
they run, after lowering and after every built-in analysis has passed:

1. lex, parse, match statements against patterns
2. definite assignment and flow analysis
3. lowering to the core tree
4. **domain checks**
5. `BubasProgram` returned

A check therefore never sees a malformed program. Everything it is handed compiled.

### 2.1. Registration is the embedder's decision

`check(ProgramCheck)` is declared on `BubasLanguage.Builder` and **not** on
[`Registrar`](SPEC.md#103-building-a-language).

> **Rationale (not normative).** `Registrar` withholds `seal()` and `skipOverlapAnalysis()` because
> they are the embedder's calls, and a library making them on the embedder's behalf would be making
> them for every other library in the same chain. A check is a stronger version of the same
> imposition: it can fail compilation of a program that never touches the registering bundle's
> vocabulary. Adding vocabulary is additive and ignorable; adding a check is neither.
>
> A library that wants to ship a check publishes it as a class. The embedder writes one more line
> and keeps the decision.

### 2.2. Any number of checks

`check` accumulates. It may be called as often as the embedder likes, including not at all, which is
the ordinary case for a language that needs none.

Checks are **independent**. Each is handed the same `ProgramView`, none sees another's results, and
the order they were registered in affects only the order their problems are reported
([9.1](#91-ordering-is-deterministic)).

**Every check runs on every compilation**, whatever earlier ones found. Stopping at the first check
that reported something would defeat the reason problems are returned rather than thrown: one
compilation should say everything that is wrong with a program.

There is no rule against registering the same check twice, because there is no name for a second
registration to collide with. It runs twice and reports twice, which is visibly redundant rather
than silently wrong. This is the one place the check SPI departs from
[`defineFunction` and its siblings](SPEC.md#103-building-a-language), where a repeated name is an
error precisely because the resulting behaviour would depend on registration order with nothing
said.

For the same reason `check` is not a definition: it neither consumes a pending `override()` nor is
affected by one.

## 3. The check interface

```java
@FunctionalInterface
public interface ProgramCheck {

    /** A rule violation, attributed to the line that broke it. */
    record Problem(String message, int line, String sourceLine) {
    }

    /** Every violation found. An empty list means the program satisfies this check. */
    List<Problem> check(ProgramView program);
}
```

A check returns problems rather than throwing. Reporting all of them lets one compilation report
everything wrong with a program, which is the same choice `VocabularyExport` makes about missing
descriptions and for the same reason: a rule-writer should not discover faults one recompilation at
a time.

## 4. The program

```java
public interface ProgramView {

    String name();

    /** Absent when the program has no {@code RETURNS} clause. */
    Optional<BubasType> returns();

    /** Every variable, in slot order, parameters first. */
    List<VariableView> variables();

    /** How many leading variables are parameters. */
    int parameterCount();

    Stream<StatementView> statements();
}

public record VariableView(String name, BubasType type, boolean isFinal) {
}
```

`statements()` is a `Stream` because it is an entry point, consumed once. Every nested accessor in
this document returns a `List`, because a recursive walk reads each one repeatedly and a `Stream`
consumed twice throws.

## 5. Statements

```java
public sealed interface StatementView {

    int line();

    String sourceLine();

    /**
     * Every directly nested statement, whatever the kind — branch arms and the otherwise, a loop's
     * body — for a walker that cares about nesting rather than shape. Empty when there is none.
     */
    List<StatementView> nested();

    record Branch(List<BlockView> arms, Optional<List<StatementView>> otherwise,
                  int line, String sourceLine) implements StatementView {
    }

    /** @param testAtEnd the body runs at least once */
    record Loop(BlockView block, boolean testAtEnd, int line, String sourceLine)
            implements StatementView {
    }

    /** A counting loop. Bounds and step are evaluated once, on entry. */
    record Count(String variable, ExpressionView from, ExpressionView to, ExpressionView step,
                 List<StatementView> body, int line, String sourceLine) implements StatementView {
    }

    record Break(int line, String sourceLine) implements StatementView {
    }

    /** @param value absent in a program with no {@code RETURNS} */
    record Return(Optional<ExpressionView> value, int line, String sourceLine)
            implements StatementView {
    }

    /** A bare call to a VOID function. */
    record Procedure(Class<?> implementation, List<ArgumentView> arguments,
                     int line, String sourceLine) implements StatementView {
    }

    /** A custom command. */
    record Invoke(Class<?> implementation, List<ArgumentView> arguments,
                  int line, String sourceLine) implements StatementView {
    }
}

public record BlockView(Optional<ExpressionView> condition, List<StatementView> statements) {
}
```

### 5.1. Empty is not absent

`Branch.otherwise()` is **absent** when the program has no `ELSE`, and **present and empty** when it
has an `ELSE` containing nothing. A `BlockView`'s `statements()` may likewise be empty.

The distinction is load-bearing: an arm with an empty body is a thing a check may want to flag, and
it is unreachable if "no such construct" and "an empty one" render alike.

### 5.2. Arms are not flattened

A `Branch` keeps one `BlockView` per `IF`/`ELSEIF` arm, in source order. Flattening the arms
together would make it impossible to say which statements belong to which condition, how many arms
there are, or whether an arm is empty.

`nested()` exists for walkers that genuinely do not care — a nesting-depth check does not — and
loses nothing, because the structure remains available on the record.

## 6. Expressions

```java
public sealed interface ExpressionView {

    BubasType type();

    int line();

    int column();

    /** Operands, in a fixed order for the kind. Empty for a leaf. */
    List<ExpressionView> children();

    enum Numeric {INTEGER, DECIMAL}

    enum Operator {ADD, SUBTRACT, MULTIPLY, DIVIDE, MODULO}

    enum Relation {EQUAL, NOT_EQUAL, LESS, LESS_OR_EQUAL, GREATER, GREATER_OR_EQUAL}

    enum Comparable {INTEGER, DECIMAL, STRING, BOOLEAN}

    enum Connective {AND, OR}

    record Constant(Value value, BubasType type, int line, int column) implements ExpressionView {
    }

    record Load(String variableName, BubasType type, int line, int column)
            implements ExpressionView {
    }

    record Element(String variableName, ExpressionView index, BubasType type, int line, int column)
            implements ExpressionView {
    }

    /** An INTEGER used where a DECIMAL is wanted. Inserted by lowering; never written by an author. */
    record Widen(ExpressionView operand, int line, int column) implements ExpressionView {
    }

    /** A value rendered as text for concatenation. Inserted by lowering. */
    record Text(ExpressionView operand, int line, int column) implements ExpressionView {
    }

    record Arithmetic(Numeric kind, Operator operator, ExpressionView left, ExpressionView right,
                      int line, int column) implements ExpressionView {
    }

    record Negate(Numeric kind, ExpressionView operand, int line, int column)
            implements ExpressionView {
    }

    record Concat(ExpressionView left, ExpressionView right, int line, int column)
            implements ExpressionView {
    }

    record Compare(Comparable kind, Relation relation, ExpressionView left, ExpressionView right,
                   int line, int column) implements ExpressionView {
    }

    record Logical(Connective connective, ExpressionView left, ExpressionView right,
                   int line, int column) implements ExpressionView {
    }

    record Not(ExpressionView operand, int line, int column) implements ExpressionView {
    }

    record Call(Class<?> implementation, List<ArgumentView> arguments, BubasType type,
                int line, int column) implements ExpressionView {
    }
}
```

### 6.1. The view is of the lowered tree

A check sees what a back end sees, not what the author typed. Coercions the source left implicit are
nodes here: `perDay = total / days` with a `DECIMAL` and an `INTEGER` is

```
Arithmetic(DECIMAL, DIVIDE, Load(total), Widen(Load(days)))
```

and `"over " + total` is `Concat(Constant, Text(Load(total)))`.

This is deliberate rather than incidental. "Never implicitly widen an `INTEGER` into a money
calculation" is exactly the kind of rule this SPI exists for, and it is checkable only if the
coercion is visible. The cost is paid by checks that do not care, through
[`ProgramChecks.unwrap`](#82-utilities).

> **Rationale (not normative).** The alternative — presenting the source tree — was rejected because
> it discards information no other part of the pipeline can recover, and because "what the author
> typed" stops being well defined the moment a second back end exists. The contract that stays true
> is the one the core tree already makes.

## 7. Arguments

One type covers a function's arguments and a command's placeholders alike.

```java
public sealed interface ArgumentView {

    /** The placeholder's name, or the function parameter's name. */
    String name();

    /** A variable the pattern named: {@code VAR} or {@code IDENTIFIER}. */
    record Variable(String name, String variableName, BubasType type, boolean isFinal,
                    Optional<ExpressionView> index,
                    Set<Precondition> preconditions,
                    Set<Postcondition> postconditions) implements ArgumentView {

        /** The pattern brings this variable into existence. */
        public boolean creates() {
            return preconditions.contains(Precondition.NEW)
                    || postconditions.contains(Postcondition.FINAL);
        }

        /** The pattern guarantees a value in it afterwards. */
        public boolean writes() {
            return postconditions.contains(Postcondition.INITIALIZED);
        }
    }

    record Expression(String name, ExpressionView expression) implements ArgumentView {
    }

    record Literal(String name, Value value) implements ArgumentView {
    }

    record Type(String name, BubasType designated) implements ArgumentView {
    }
}
```

### 7.1. A placeholder is not an expression

A command's argument is not necessarily an expression, and representing one as an `ExpressionView`
would state three falsehoods: that a `{type:T}` placeholder is an expression, that a `{literal:X}`
is one, and — worst — that a `{var:x}` is a *read*. `ROUTE claim TO approver AT centre` and
`DECLARE x INTEGER` **write** and **declare**.

That information is not in the argument at all; it is in the pattern, as `preconditions` and
`postconditions`. A check that cannot see it cannot tell a write from a read, which is most of what
there is to say about a command.

### 7.2. Actual finality is not the pattern's demand

`isFinal` is the variable's own finality. `preconditions` is what the *pattern requires*. A pattern
may leave finality open — `{var:total}` with no mutability prefix admits a final variable and a
mutable one alike — so a check that must know cannot infer it from the pattern.

### 7.3. `VAR` and `IDENTIFIER` share one record

Both arrive as one core argument, and the observable difference is whether `index` is present. The
pattern-level distinction — that only an `IDENTIFIER` may create — is carried by `creates()`.

## 8. Derived views

### 8.1. Call sites

A *call* is not a primitive of `ProgramView`. It is derivable: a `Procedure` or `Invoke` statement,
or a `Call` expression anywhere inside one.

```java
public record CallSite(Class<?> implementation, List<ArgumentView> arguments,
                       int line, String sourceLine) {
}
```

### 8.2. Utilities

```java
public final class ProgramChecks {

    /** Every call — statement-form and nested in an expression — in source order. */
    public static Stream<CallSite> calls(Stream<StatementView> statements);

    /** The operand a coercion wraps, or the expression itself. */
    public static ExpressionView unwrap(ExpressionView expression);
}
```

`calls` walks nested statements *and* expression trees. Most function calls are nested inside
expressions — `total = TOTAL_OF(claim)` — so a walk of statements alone would miss them.

### 8.3. A call is identified by class

`implementation()` is the `Class<?>` registered with `defineFunction` or `defineStatement`.

> **Rationale (not normative).** A command's registered name is its pattern skeleton, so
> `"ROUTE _ TO _ AT _"` is the only identity a `.bu` file can express and the right one for BUNIT.
> In Java it is the wrong one: rewording a keyword changes the skeleton, and any check comparing
> against the old string keeps compiling while silently matching nothing. A class reference is
> rewritten by every refactoring tool. One class is one function or one command, so the identity is
> exact.

`name()` remains available on `ArgumentView` and elsewhere for diagnostics. Comparing against it is
the mistake this rule exists to prevent.

## 9. Failure

When any check returns a problem, `compile()` throws a `BubasCheckException`.

```java
public class BubasCheckException extends BubasException {

    public List<ProgramCheck.Problem> problems();
}
```

`BubasException` carries one line and one source line, which cannot represent problems on twelve
different lines. `BubasCheckException` overrides `getDiagnostic()` to render each problem with its
own position:

```
line 12: 'a.b.c' is not a field of Order
        total = GET_FIELD("a.b.c")
line 19: 'x.y' is not a dotted path
        name = GET_FIELD("x.y")
```

The inherited `getLine()` and `getSourceLine()` report the first problem, so anything catching a
plain `BubasException` still behaves.

### 9.1. Ordering is deterministic

Problems appear in check registration order, and within a check in the order the check returned
them. A check walking `statements()` in order therefore produces source order. This makes a failing
build diffable.

## 10. Obligations on an implementation

**A check must be safe for concurrent use.** It is registered once and held by a sealed
`BubasLanguage`, which is shared freely across threads; two threads compiling different programs
call the same instance. Accumulating problems in an instance field is a defect.

**A check must not do work per compilation that belongs in its constructor.** Chapter 31's
compile-on-save — and compile-on-keystroke, where an editor offers it — runs every check on every
edit. A schema is loaded and compiled when the check is constructed, not when it runs.

**A check must not throw.** It returns problems. An exception escaping `check` is a defect in the
check, and the implementation reports it as such rather than letting it pass for a compilation
error.

## 11. Packaging

| Artefact | Module |
|---|---|
| `ProgramCheck`, `ProgramView`, `StatementView`, `ExpressionView`, `ArgumentView`, `BlockView`, `VariableView`, `CallSite` | `bubas-api` |
| `ProgramChecks` | `bubas-api` |
| `BubasCheckException` | `bubas-api` |
| `Kind`, `Precondition`, `Postcondition` — **moved** from `bubas-analyser.pattern` | `bubas-api` |
| The adapters from `CoreProgram`, `CoreStatement`, `CoreExpression`, `CoreArgument` | `bubas-analyser` |
| Ready-made, domain-independent checks | a new optional module |

The view types are sealed, so their permitted records must live in one module; `bubas-analyser`
constructs them rather than implementing them. That means a view tree is built eagerly per
compilation. For programs of the size BUBAS is for this is negligible, and every registered check
walks the whole tree anyway.

`Precondition` and `Postcondition` move because a check cannot read a pattern's demands without
them, and a check depends on `bubas-api` alone.

## 12. Interaction with BUNIT

`BunitSuite.of(language, subject)` compiles the subject, so checks run there too and a violation
surfaces inside a test as a compilation failure. This is intended: a test subject should satisfy the
domain's rules. It is worth knowing before somebody debugs it as a BUNIT fault.

## 13. Examples

### 13.1. A literal validated against an external model

```java
public final class SchemaPathCheck implements ProgramCheck {

    private final JsonSchema schema;   // compiled in the constructor, never in check()

    public List<Problem> check(ProgramView program) {
        final var problems = new ArrayList<Problem>();
        ProgramChecks.calls(program.statements())
                .filter(call -> call.implementation() == GetField.class)
                .forEach(call -> {
                    if (call.arguments().get(0) instanceof ArgumentView.Literal(var n, var value)) {
                        if (!schema.resolves(value.asString())) {
                            problems.add(new Problem("'" + value.asString() + "' is not a field",
                                    call.line(), call.sourceLine()));
                        }
                    } else {
                        problems.add(new Problem("the path must be a literal",
                                call.line(), call.sourceLine()));
                    }
                });
        return problems;
    }
}
```

### 13.2. A structural limit

```java
public final class MaxLoopNestingCheck implements ProgramCheck {

    private final int limit;

    public List<Problem> check(ProgramView program) {
        final var problems = new ArrayList<Problem>();
        walk(program.statements().toList(), 0, problems);
        return problems;
    }

    private void walk(List<StatementView> statements, int depth, List<Problem> problems) {
        for (final var statement : statements) {
            final var deeper = statement instanceof StatementView.Loop
                    || statement instanceof StatementView.Count;
            final var now = deeper ? depth + 1 : depth;
            if (deeper && now > limit) {
                problems.add(new Problem("loop nested " + now + " deep, the limit is " + limit,
                        statement.line(), statement.sourceLine()));
            }
            walk(statement.nested(), now, problems);
        }
    }
}
```

Nothing in it is specific to a domain, which is why it belongs in the optional module rather than
in `bubas-api`.

## 14. Open questions

**Whether the view types should be sealed.** They are, in this document. Sealing gives a check
exhaustive `switch` and record deconstruction, and an author who does not want to be broken by a
future statement kind writes a `default` arm. The cost is that adding a kind is a source-breaking
change for any check that chose exhaustiveness, and that the view tree must be constructed eagerly.
Both are judged acceptable; neither is free.

**Whether a `Break` should name the loop it leaves.** It carries a loop identity in the core tree.
BUBAS has no labels, so a break always leaves the innermost loop of its kind, and no proposed check
needs more. Omitted until one does.

**Whether checks should see the vocabulary.** A check receives a program, not the language it was
compiled against. A rule spanning both — "this function may only be called on a variable this
command declared" — is expressible today; one needing the registered set is not. No concrete case
has come up.

**Where this belongs in `SPEC.md`.** This document is separate while the design settles. The
material is §10's — the Java integration — and should move there once implemented, leaving this file
to hold the rationale the specification proper does not carry.
