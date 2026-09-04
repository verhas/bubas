# Defining commands

<!-- abstract -->
The pattern language: how `REJECT claim, "over the limit"` becomes a statement, with placeholders,
kinds, type constraints, and conditions on what a variable must be before and after. How the
analysis at sealing time proves no two commands can ever match the same line.
<!-- /abstract -->

---

## A statement is a shape

A function is identified by its name and called with brackets. A statement is matched as a **whole
line**, against a shape you write:

<!--INCLUDE
from: "../../bubas-doc/src/test/java/javax0/bubas/doc/expense/Expense.java"
start: '// snippet: approve'
end: '// end snippet'
prefix: "```java"
postfix: "```"
margin: 0
_content_generated_: 311:md5:a58808dcfa3ed7ee8efdcb70b4d0af69
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```java
@BubasDescription("Approves a claim for payment.")
public static final class Approve {
    public void call(StatementContext ctx, ExpressionArg claim) {
        final var filed = claim.evaluate().as(Report.class);
        ctx.log("DECISION", "approved " + filed + " for " + filed.total());
    }
}
```
<!--/INCLUDE-->

<!--INCLUDE
from: "../../bubas-doc/src/test/java/javax0/bubas/doc/expense/Expense.java"
start:
  pattern: '\.defineStatement\("APPROVE '
  include: true
end:
  pattern: '\.defineStatement\("REJECT'
  include: false
prefix: "```java"
postfix: "```"
margin: 0
_content_generated_: 82:md5:07996aa08e66b2dc64fa1b2aad449fad
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```java
.defineStatement("APPROVE {expression/Report:claim}", Approve.class)
```
<!--/INCLUDE-->

That second line is a call on the builder, alongside the `defineFunction` and `defineOpaqueType`
calls of chapter 21 — a statement is registered exactly like anything else, and the only difference
is that its name is a shape rather than a word.

Everything outside braces is a keyword the rule must write literally. Everything inside is a
**placeholder**: a hole, with a kind, usually a type constraint, and a name.

That is why statements can have words in the middle. `ROUTE claim TO approver AT centre` is one
statement whose shape happens to include `TO` and `AT`, and the words carry meaning for the reader
rather than being punctuation.

## Placeholders

The full form has four zones, all optional:

```
{ prefixes > kind[/constraint] : name > postfixes }
```

**Kind** is what the hole accepts. Five of them, and the two that matter most are `expression` —
anything that produces a value, handed to your handler unevaluated — and `identifier`, a bare
variable name. `literal` insists on a compile-time constant, `type` takes a type designator, and
`var` is `identifier` with an appetite for an index, so `totals[3]` matches it.

**Constraint** is the type: `/Report` means the expression must be a `Report`, `/STRING` a string.
It can also name another placeholder in the same pattern, meaning *the same type as that one* —
which is how `DECLARE` ties a variable's type to the type designator later in its own line.

**Name** is what the handler's parameter is called and what the vocabulary document shows. A
placeholder may not be named after a type, checked at `seal()`, for the same reason chapter 22's
variables cannot be: `/Report` would otherwise have two readings.

**Prefixes and postfixes** are conditions on a variable, and they are the interesting part.

## Before and after

<!--INCLUDE
from: "../../bubas-doc/src/test/java/javax0/bubas/doc/expense/Expense.java"
start: '// snippet: routing-language'
end: '// end snippet'
prefix: "```java"
postfix: "```"
margin: 0
_content_generated_: 510:md5:3b550381c84de2655f29a839cd23a42a
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```java
/** Stage 6: one operation, two answers, and the budget they point at. */
static BubasLanguage.Builder routing() {
    return screening()
            .defineOpaqueType("CostCentre", CostCentre.class)
            .defineFunction("BUDGET_LEFT", BudgetLeft.class)
            .defineStatement("ROUTE {expression/Report:claim}"
                    + " TO {new > identifier/STRING:approver > initialized}"
                    + " AT {new > identifier/CostCentre:centre > initialized}", Route.class);
}
```
<!--/INCLUDE-->

`{new > identifier/STRING:approver > initialized}` says three things.

`new >` is a **precondition**: this variable must not exist yet. The statement creates it.

`identifier/STRING:approver` is the hole itself.

`> initialized` is a **postcondition**: after this statement runs, the variable holds a value. That
is what lets chapter 8's flow analysis know the rule may read `approver` on the next line, without
knowing anything about what your handler does.

This is how `DECLARE` works too, and it is why chapter 4 could say that declaration is not syntax.
`DECLARE {new > identifier/T:name > declared} {type:T}` is an ordinary pattern shipped in a module
you may decline to install.

One consequence to know before it bites: **a statement that declares may only appear at the top
level of a program.** Not inside an `IF`, not inside a loop. BUBAS has no local variables, so a
declaration inside a block would look scoped while being global, and the message says so.

## Writing into a variable

A handler receives a `VariableArg` for a placeholder it writes, and `set` puts a value in:

<!--INCLUDE
from: "../../bubas-doc/src/test/java/javax0/bubas/doc/expense/Expense.java"
start: '// snippet: route'
end: '// end snippet'
prefix: "```java"
postfix: "```"
margin: 0
_content_generated_: 946:md5:5e10210323fa0090de8fa9093ecc341b
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```java
/**
 * Two answers from one lookup, which is the only reason this is a command rather than a
 * function. Who signs and which budget it lands on are decided together, by one reading of the
 * approval policy; asking twice could get answers from two different readings.
 */
@BubasDescription("""
        Works out who has to approve a claim and which budget it will be charged to.
        Both come from one reading of the approval policy, so they are always consistent.
        """)
public static final class Route {
    public void call(StatementContext ctx, ExpressionArg claim, VariableArg approver,
                     VariableArg centre) {
        final var filed = claim.evaluate().as(Report.class);
        approver.set(filed.total().compareTo(new BigDecimal("1000")) > 0
                ? "the finance director" : "the line manager");
        centre.set(new CostCentre("CC-" + filed.id, new BigDecimal("2500.00")));
    }
}
```
<!--/INCLUDE-->

`Route` there is a static nested class, and so is every handler in this book. Nothing requires it
— a handler may be a top-level class in a file of its own — but a vocabulary of a dozen operations
is a dozen small classes, and keeping them nested inside the class that registers them puts the
whole language in one file where it can be read as one thing. The only requirement is that a nested
handler be **static**: the runtime constructs it with no arguments, so it cannot be an inner class
needing an enclosing instance.

An `ExpressionArg` is different: it arrives **unevaluated**, and calling `evaluate()` is what runs
it. That is deliberate, and it is the one power a statement has that a function does not — a
statement decides *whether* and *when* its arguments run. `APPROVE` evaluates its claim once. A
conditional statement could evaluate one branch and not the other.

Use it rarely, and only for a reason you can name. An operation whose arguments may or may not run
is harder to reason about than one that always evaluates, and chapter 12's reader has to work out
which it is.

`ROUTE` is a case where the choice makes itself. It cannot decide anything without knowing which
claim it is routing, so it evaluates its expression immediately and unconditionally — and if it did
not, it would have nothing to write into either of its two targets. The power exists for the
statement that genuinely needs to skip an argument; a statement that needs all of its arguments
should just take them.

## Saying what a command puts there

`ROUTE` writes two variables, and the compiler knows only that. It cannot know *what* — the approver
comes out of whatever `Route` does with the claim, and there is nothing in the pattern, or in Java,
that could tell it.

That is the ordinary case and needs nothing. It is worth knowing about the exception, because
chapter 8's refusals run on it.

A command that does not compute but *copies* can say so. Assignment is such a command — it is an
ordinary statement in the standard module, not a language built-in — and it declares what lands
where:

<!--INCLUDE
from: "../../bubas-support/src/main/java/javax0/bubas/support/Assign.java"
start:
  pattern: '^@BubasAssigns'
  include: true
end:
  pattern: '^    public void call'
  include: false
prefix: "```java"
postfix: "```"
margin: 0
_content_generated_: 217:md5:889d1899a2f3300f8de2a45ff2d57aa4
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```java
@BubasAssigns(target = "name", value = "value")
public final class Assign {

    public static final String PATTERN =
            "{mutable:declared > var:name > initialized} = {expression/name:value}";

```
<!--/INCLUDE-->

`target` names the variable placeholder, `value` the expression placeholder whose value it receives.
That one line is why the compiler knows that after `strict = TRUE` the variable holds `TRUE`, and
therefore why the `IF` four lines later has an answer.

Three properties of the declaration are worth stating plainly.

**It is optional and it is a claim.** A command that declares nothing is not deficient; it is
opaque, which is what nearly every command should be. A command that declares this and then writes
something else has lied to an analysis, and nothing checks it.

**It repeats.** One statement may fill several variables, and each occurrence names one target. A
command filling two and declaring one is describing itself accurately — the declared variable is
known afterwards, the other is not. No two occurrences may name the same target; `seal()` refuses
that, because two claims about one variable cannot both be the story.

**Anything you hand a command is assumed written.** Nothing at run time stops a handler calling
`set` on a variable its pattern only claimed to read, so the compiler forgets the value of every
variable a statement touches, and puts back only what was declared. If your command is handed
something it merely reads, the compiler is being pessimistic about it, and that costs nothing but a
refusal it will not make.

## Why this command is a command

Worth restating, because the temptation runs the other way.

`ROUTE` produces two values, decided together by one reading of the approval policy. Had it produced
only the approver, it should have been `approver = FIND_APPROVER(claim)` — a writing command with a
single target is a function wearing a disguise, harder to read, harder to compose, and it declares
a variable as a side effect of being called.

**More than one answer, decided together.** That is the test — not "it writes rather than
returns". Writing into a variable is only interesting when there is more than one place to write
to; with one, returning the value says the same thing and reads better.

## Sealing proves the shapes cannot collide

Two statements whose shapes could both match one line would make a program's meaning depend on
which was registered first. BUBAS refuses that outright, at `seal()`.

The analysis is exhaustive rather than heuristic: it treats each pattern as an automaton and looks
for any line both would accept. If one exists, sealing fails with a message naming both patterns,
at startup, before any program has been written.

Two practical consequences. **You cannot ship an ambiguous vocabulary** — not to a test environment,
not to production. And **adding a statement can break sealing**, which is the right time to find out:
a new pattern that overlaps an existing one is caught by the build that added it.

If it fires, the fix is nearly always to add a distinguishing keyword. `SEND claim TO "finance"`
and `SEND claim, "finance"` collide; `SEND claim TO "finance"` and `NOTIFY claim, "finance"` do
not.

## Designing shapes people can read

Three habits, in decreasing order of how much they matter.

**Read it aloud.** `ROUTE claim TO approver AT centre` is a sentence. `ROUTE claim, approver,
centre` is an argument list. Both work; only one tells the reader what the second and third things
are for.

**Put the keywords where the meaning changes.** `TO` and `AT` are doing real work — they say which
hole is the person and which is the budget. A keyword that carries no such information is noise.

**Keep the set small.** The overlap analysis is exhaustive, so a large command set stays correct,
but chapter 11's reader has to hold it in view as one thing. Ten or a few tens is the size this is
built for.
