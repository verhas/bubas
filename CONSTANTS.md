# BUBAS Constant Evaluation — Specification

**Version** 1.3 · **Status** Implemented — `CoreArithmetic`, `ConstantFolding`, `Constants` and
`DeadCode` in `bubas-analyser.core`, with the corpus under `bubas-test/…/scripts/constant`. Its precursor, the
`MathContext` sealed into the language, is what makes decimal division evaluable
([4](#4-decimal-division)). See [`SPEC.md`](SPEC.md) for the language itself and
[`CHECKS.md`](CHECKS.md) for domain checks.

Every expression whose value is fixed at compile time is evaluated at compile time, and everything
that fact makes visible is rejected. A branch that cannot be taken, a loop body that cannot run, an
arithmetic that cannot succeed: each is a mistake the author should be told about, not a behaviour
to preserve.

This document is the contract for that pass. Where it and the implementation disagree, this
document wins.

**This change is not backward compatible.** Programs that compile today will stop compiling. That
is the intent, not a side effect.

---

**Contents**

<!--TOC min-level: 2
max-level: 2
_content_generated_: 468:md5:8f1f05a55602c046354af9f96731b289
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
- [1. Scope](#1-scope)
- [2. Position in the pipeline](#2-position-in-the-pipeline)
- [3. What is constant](#3-what-is-constant)
- [4. Decimal division](#4-decimal-division)
- [5. Evaluation](#5-evaluation)
- [6. Rejections](#6-rejections)
- [7. Diagnostics](#7-diagnostics)
- [8. Interaction with domain checks](#8-interaction-with-domain-checks)
- [9. Settled variables](#9-settled-variables)
- [10. Testing](#10-testing)
- [11. Open questions](#11-open-questions)
<!--/TOC-->

---

## 1. Scope

Two things, in this order:

1. **Evaluation.** A constant expression is replaced by its value in the core tree.
2. **Rejection.** What the evaluated values reveal — dead branches, dead loop bodies, arithmetic
   that always traps — is a compile error.

The first is an optimisation nobody would notice. The second is the reason to do it.

### 1.1. What this is not

It is not constant *propagation*. A variable holding a constant is not a constant here; see
[9](#9-settled-variables).

It is not dead-code *elimination*. Nothing is deleted. Unreachable code is reported, and the author
deletes it. A compiler that silently removed the branch would be hiding the mistake it just found.

## 2. Position in the pipeline

Constant evaluation needs types: knowing that `1 + 2` is integer addition rather than concatenation
is what makes it evaluable. Types are computed in `Lowering` and nowhere else.

The new rejections therefore come after lowering. Everything the compiler already did stays where
it was:

1. lex, parse, match statements against patterns
2. definite assignment and reachability (`FlowAnalyser`), which also builds the symbol table
3. lowering to the core tree
4. **constant evaluation** (`ConstantFolding`) — reads the language's `MathContext`
   ([5.2](#52-the-evaluator-reads-the-rounding-policy))
5. **dead-code rejection** (`DeadCode`)
6. domain checks ([`CHECKS.md`](CHECKS.md))
7. `BubasProgram` returned

> **Rationale (not normative).** An earlier draft moved flow analysis after lowering so that every
> dead-code rejection — the ones [`SPEC.md` §8.3](SPEC.md#83-rejected-at-compile-time) already made
> and the ones added here — would live in one pass. Building it showed the move was unnecessary,
> and it was dropped.
>
> Two things removed the need. Definite assignment needs no constant knowledge, because a constant
> condition is an *error* rather than a branch to be reasoned about
> ([6.5](#65-reachability-is-unchanged-by-constants)) — the rule that looks like extra strictness is
> what keeps the existing analysis untouched. And the one new rule that sounded like reachability,
> "a loop nothing leaves" ([6.2](#62-a-loop-that-cannot-end)), is a structural query rather than an
> analysis: lowering has already resolved every `EXIT` to a loop identity, so it is a lookup on the
> folded tree.
>
> The second prize claimed for the inversion is therefore not collected. `FlowAnalyser` still keeps
> its own stack of enclosing loops to validate `EXIT FOR` and `EXIT DO` while `Lowering` separately
> assigns loop identities — two mechanisms for one fact, as before. Unifying them is a refactoring
> on its own merits, not something this work needs.
>
> What the order costs is diagnostic precedence: a program with both an uninitialised read and a
> constant condition reports the read. Both are errors, and the program is rejected either way.

## 3. What is constant

An expression is constant when every operand is constant and the operation is one whose result
depends on nothing but its operands.

| Node | Constant | |
|---|---|---|
| `Constant` | yes | |
| `Arithmetic(INTEGER, …)` | yes | may trap; see [6.4](#64-arithmetic-that-always-traps) |
| `Arithmetic(DECIMAL, ADD/SUBTRACT/MULTIPLY)` | yes | exact, and the result scale is a function of the operand scales |
| `Arithmetic(DECIMAL, DIVIDE)` | yes | since the `MathContext` moved to the language; see [4](#4-decimal-division) |
| `Negate` | yes | integer negation may trap |
| `Not`, `Logical` | yes | |
| `Compare` | yes | decimals compare by value, never by scale |
| `Concat`, `Text` | yes | |
| `Widen` | yes | |
| `Load`, `Element` | no | |
| `Call` | only if declared | see [3.1](#31-a-function-is-constant-only-if-it-says-so) |

### 3.1. A function is constant only if it says so

A function may read the host, the clock or a database, and nothing about its signature says which.
Purity is therefore never inferred — it is declared, with `@BubasMemoizable` on the implementation
class, and a call is folded only when the function carries it and every argument is known.

Beyond the declaration, everything crossing the boundary has to be a value a compiled program can
hold: every parameter and the result must be `INTEGER`, `DECIMAL`, `STRING` or `BOOLEAN`. An array
is a store, an opaque value is a Java object, a wildcard is neither, and a variadic call marshals
its arguments into an array. A function mentioning any of those is never folded, however pure it is,
so declaring it static is true and idle.

**One part of the claim is a type rather than a promise.** A memoizable function's method takes a
`CoreContext`, which has no `service` method on it, so a call to one does not compile; `seal()`
refuses one whose first parameter is anything else, so the mistake surfaces at startup
rather than on whichever compilation first knows all the arguments. Everything else — a clock read
through a static field, a file, a cache — nothing can check, and a function that declares this
falsely will answer at compile time with a value a run would not have produced. When in doubt, leave
the annotation off; nothing is lost but a fold.

**Refusing is allowed and useful.** `ctx.error` during folding is a compile error at the line of the
call. Answering the same way every time is what the annotation claims, so a function refusing these
arguments while compiling would refuse them on every run: reporting it now is the same answer,
earlier. `TO_INTEGER("twelve")` is not a number in any run, and saying so while compiling beats
waiting for the run that reaches it.

**Logging is allowed and goes nowhere.** A memoizable function has every right to log — the log does not
decide anything, so it is no reason to decline a fold. During a fold it is discarded, because the
run that would have written the line may happen any number of times and a compilation is not one of
them.

> **Rationale (not normative).** The declaration sits on the class rather than in
> `defineFunction`, beside `@BubasDescription` and `@BubasCommandName`, for the reason a command is
> identified by class: the claim belongs to the implementation, travels with it into whatever
> language registers it, and cannot drift away from the code it is about.

## 4. Decimal division

`ADD`, `SUBTRACT` and `MULTIPLY` are exact and never consult a `MathContext`. `DIVIDE` reads one,
and until recently that made it the single operation this pass could not touch: the context
belonged to the `Interpreter`, one compiled program was reused across interpreters that set it
differently, and a folded division would have frozen one run's answer into every run.

**That obstacle has been removed, and removing it was the precursor to this work.** The policy is
now set on `BubasLanguage.Builder.mathContext` and sealed at `seal()`, before any program is
compiled. It is a property of the language a program belongs to, so every run of a compiled program
divides identically — see [`SPEC.md` §6.3](SPEC.md#63-decimal-arithmetic). Division is therefore as
constant as any other decimal operation.

Integer division never had the dependency and was always constant.

### 4.1. `MathContext.UNLIMITED`

Under it a quotient with no finite expansion throws rather than rounding. Today that is a runtime
failure on the first execution that reaches the division; once constants are evaluated, a constant
one becomes a compile error by [6.4](#64-arithmetic-that-always-traps). This is a strict
improvement and needs no separate rule.

## 5. Evaluation

A constant expression is replaced by `Constant(value, type, token)`, where `token` is the token of
the outermost evaluated node, so diagnostics keep pointing where they point today.

**The evaluator and the interpreter must share one implementation of every operation.** The
arithmetic, comparison and text-rendering primitives currently private to the interpreter move into
`bubas-analyser.core`, and the interpreter calls them there. Two implementations would agree on
everything except the edge cases, and would disagree invisibly — a folded expression and an
unfolded one differing only where it matters most.

### 5.1. Growth is bounded

Exact decimal multiplication adds scales, so a chain of constant multiplications grows. The
evaluator refuses to fold a result exceeding an implementation-defined precision, leaving the
expression in place, rather than compiling an unbounded value into the program. The limit exists to
stop a pathological source from consuming the compiler, not to make a semantic distinction.

Division needs no such limit: a quotient is bounded by the `MathContext`'s precision. The exception
is `MathContext.UNLIMITED`, where a non-terminating quotient throws rather than growing, which
[4.1](#41-mathcontextunlimited) covers.

### 5.2. The evaluator reads the rounding policy

Constant evaluation is **not** a function of the core tree alone. Folding `100.00 / 3.0` requires
the same `MathContext` the interpreter would have used, so the pass takes the `BubasLanguage`
alongside the program — as `Lowering` already does.

Division is the only operation this applies to. Every other constant result follows from its
operands.

The consequence is worth stating plainly: **a folded constant is a property of the program and the
language together, not of the source.** One source compiled against two languages differing only in
rounding yields two different constants. That is correct — it is exactly what the two interpreters
would have computed — but it means a test asserting a folded value has to name the language it
compiled against, and a core tree is not comparable across languages.

## 6. Rejections

Each of the following is a compile error. All extend
[`SPEC.md` §8.3](SPEC.md#83-rejected-at-compile-time).

### 6.1. A constant branch condition

The condition of an `IF` or an `ELSEIF` may not be constant. If it is false the arm cannot run; if
it is true the arms after it cannot. Either way one of them is dead, and the author meant something
they did not write.

The remedy is in the diagnostic: delete the arm, or delete the `IF` and keep its body.

### 6.2. A loop that cannot end

Unlike a branch, a loop with a constant condition is not automatically wrong: `DO WHILE TRUE` with
an `EXIT DO` in the body is how a loop with a computed exit is written. The error is a constant
condition that keeps the loop running with **nothing that can leave it** — exactly the "provably
non-terminating loop" §8.3 already names and could not previously detect.

Leaving it means more than an `EXIT` naming the loop itself. An `EXIT` naming a loop further out
unwinds through this one, and a `RETURN` leaves the program; each ends the loop, and each makes it
legal. Only an `EXIT` belonging to a loop nested *inside* the body fails to help. So this is
accepted, the `EXIT FOR` ending the inner loop on its first pass:

```basic
FOR i = 1 TO 4
    DO WHILE TRUE
        EXIT FOR
    END DO
END FOR
```

A constant condition that stops the loop is dead in the other direction: `DO WHILE FALSE` never
runs its body, and `DO … END DO UNTIL TRUE` runs it exactly once. Both are errors.

### 6.3. A `FOR` loop that cannot iterate

With constant bounds and step:

- a range that never satisfies the test — `FOR i = 1 TO 0`, or `FOR i = 0 TO 10 STEP -1` — has a
  body that cannot run
- a zero step never terminates. §7.5 makes this a runtime error; a constant zero is a compile error

### 6.4. Arithmetic that always traps

A constant expression that overflows, divides by zero or takes `MOD 0` is an error where it is
written, whether or not control could reach it. `1 / 0` inside a branch that never runs is two
mistakes, and both are reported.

### 6.5. Reachability is unchanged by constants

Because a decided condition is an error, no arm is ever known-unreachable *because of* its
condition. Definite assignment and reachability continue to treat every arm as possible, and need
no constant reasoning of their own.

> **Rationale (not normative).** This is the main practical argument for rejecting constant
> conditions rather than folding them away. Folding would oblige the flow analyser to model which
> arms survive, when a program that needs such modelling is one nobody should have written.

## 7. Diagnostics

Each rejection reports the line and source line of the construct at fault: the condition for
[6.1](#61-a-constant-branch-condition) and [6.2](#62-a-loop-that-cannot-end), the `FOR` header for
[6.3](#63-a-for-loop-that-cannot-iterate), the operator token for
[6.4](#64-arithmetic-that-always-traps).

They are ordinary `BubasException`s. A program is rejected at the first, in source order.

## 8. Interaction with domain checks

Constant evaluation runs **before** domain checks, so a check sees the evaluated tree.

This strengthens checks more often than it weakens them. `CHECKS.md` §13.1 validates a literal path
argument and rejects anything else; with evaluation, `GET_FIELD("order." + "total")` arrives as a
`Constant` the same check can validate.

What it costs is coercions around literals: `Widen(Constant)` and `Text(Constant)` no longer
survive to be seen. `CHECKS.md` §6.1 justifies exposing the lowered tree with exactly these two
nodes, but both its examples already coerce a *variable* — `Widen(Load(days))`, `Text(Load(total))`
— and those are untouched. The rule it motivates, "never implicitly widen an `INTEGER` into a money
calculation", is about integer variables entering decimal arithmetic; a widened literal is the case
it does not care about. No change to `CHECKS.md` is required.

## 9. Settled variables

Almost nobody writes `IF FALSE`. What people write is a variable set two lines earlier, and the
rules above would be a formality if they stopped at literals:

```basic
n = 5
IF n > 10 THEN
```

`n` is not constant — the next line may assign it again — but *where the condition reads it* it
holds 5, and the condition is answered before the program runs. Every rule in
[6](#6-rejections) therefore applies to what is **settled at that point**, not only to what is
constant throughout.

### 9.1. What is carried

The analysis walks the program carrying a value for each variable whose value is certain there.

- **A branch** tests every arm in the state it was entered with; a condition cannot change a
  variable, since a function may not reach the store. At the join a value survives only if every
  path arrives with the same one — including the path through a missing `ELSE`.
- **A loop** forgets everything its body writes, before the condition and for good — unless it can
  be *followed* ([9.4](#94-following-a-loop)), in which case what it leaves behind is known exactly.
  Forgetting is the fallback, and the body may run any number of times, so nothing it touches is
  certain in the condition, inside the body on a second pass, or after the loop.
- **A `FOR`** forgets its loop variable too, and evaluates its bounds in the state on entry, which
  is when they are evaluated for real. It is followed on the same terms, and then the counter is
  known afterwards as the first value that failed the test.
- **`RETURN` and `EXIT`** leave nothing behind that anything reads. A path that ends in one still
  contributes to a join, which loses precision and never gains it.

### 9.2. What is learned

Only from a command that says so, with `@BubasAssigns(target, value)` naming two of its
placeholders. `Assign`, `DeclareInitialized` and `DeclareFinal` declare it; anything else writes a
value the compiler cannot predict.

The declaration is optional and nothing needs it. Without it the analysis simply learns nothing from
that command, which is also how an embedder's own assignment syntax joins in: it declares the same
thing, and the analyser goes on knowing no vocabulary.

**It repeats, because one statement may write several variables.** `SPLIT name INTO first AND last`
fills two, and each occurrence names one target:

```java
@BubasAssigns(target = "low", value = "from")
@BubasAssigns(target = "high", value = "to")
```

Partial is meaningful and correct. A command that fills two variables and declares one of them is
describing itself accurately: the declared one is learned, the other is forgotten like anything
else. Several targets may draw on the same value; no two may name the same target, which would be
two claims about one variable, and `seal()` refuses it.

**Anything handed to a command is assumed written.** `set` is not guarded at run time, so a handler
can write a variable its pattern only claimed to read, and an analysis trusting the postcondition
would be trusting something nothing enforces.

> **Rationale (not normative).** Being wrong in that direction is the only safe way to be wrong. A
> value believed and not held rejects a correct program; a value forgotten only lets a mistake
> through. The rules here refuse programs, so the analysis has to be timid.

### 9.3. The flag this rejects

```basic
DECLARE debug BOOLEAN FINAL = FALSE
...
IF debug THEN
```

This is now an error, and deliberately. A flag a program's behaviour depends on belongs in the
program's parameters, where the embedder supplies it and a BUNIT test can set it — at which point
it is not settled, and the code under it is not dead.

A parameter is the general answer: it is the one thing in a program whose value the compiler cannot
know. Anything written into the source is known where it is read.

### 9.4. Following a loop

A loop whose every value the analysis already holds is one it can run, and running it is the
difference between knowing that a loop writes `n` and knowing what it leaves in it:

```basic
n = 5
limit = 7
DO WHILE n < limit
    n = n + 1
END DO
IF n = 7 THEN
```

Seven, so the `IF` is a question with an answer, and one of its two ways is dead. Nothing about that
program is written as a constant.

**The walk that follows a loop is not the walk that refuses things**, and the two cannot be one.
Inside a followed loop *every* condition has an answer on every pass — `IF n = 1` in the body is
decided each time round — but the answer differs between passes, which is not the dead code
[6](#6-rejections) is about. Rejecting is therefore done once, conservatively, with everything the
body writes forgotten; following is done separately and refuses nothing. What following changes is
only the state the loop leaves behind.

It gives up rather than guesses, and gives up often:

- a condition it cannot decide, or a statement whose effect nothing declared
- a body that can `EXIT` or `RETURN` — an abrupt exit is a path this does not model
- arithmetic that traps. That trap would happen on every run and is worth reporting, but reporting
  it would mean trusting this walk to be right about which pass it reached; it is left alone
- a budget spent, so a loop the analysis *can* follow but that runs a hundred million times cannot
  hang a compilation

Giving up costs only precision that was never there before, which is what makes the whole thing
safe to have.

**Definite assignment does not benefit from any of it.** It runs before lowering, judges a loop by
its shape — a top-tested one guarantees nothing, because the body may not run — and never learns
that this particular loop provably runs four times. So a variable first written inside a followed
loop still needs an initialiser above it, or a loop that tests at the bottom.

> **Rationale (not normative).** This looks like an oversight and is a choice. The rejections can
> afford to be clever because being clever only ever refuses programs that were wrong anyway. If
> definite assignment were clever, *acceptance* would depend on it: changing `limit = 7` to a value
> the compiler cannot read would produce "read before it is assigned" on a line nowhere near the
> edit, and the four loop shapes would stop being a rule an author can apply by reading. `SPEC.md`
> §7.5 exists to make that rule readable, and it stays readable by staying dumb.

> **Rationale (not normative).** One case falls out and is deliberately not taken. A pass that
> changes nothing will change nothing next time, so a loop whose state repeats provably never ends —
> something [6.2](#62-a-loop-that-cannot-end) cannot see, having forgotten what the body writes.
> That is a new refusal, and a refusal deserves its own decision rather than arriving as a side
> effect of an analysis added for a different reason.

## 10. Testing

The evaluator and the interpreter must agree, and after [5](#5-evaluation) they share their
primitives, so what remains to test is the pass itself: every program in the suite, run with
evaluation on and off, must produce identical results and identical failures. Because the rounding
policy is now a property of the language, "under several `MathContext` settings" means compiled
against several languages that differ only there — which is also the cheapest test that the policy
really does travel with the program.

Division is what that test is for. Sharing primitives guarantees the *operation* agrees; it does not
guarantee the evaluator was handed the *same context* the interpreter reads. Folding `1.0 / 3.0`
against a language at 5 digits and another at 10 catches the plumbing mistake that shared code
cannot.

Each rejection in [6](#6-rejections) needs a program that triggers it and a neighbouring one that
does not — particularly [6.2](#62-a-loop-that-cannot-end), where `DO WHILE TRUE` with and without a
reachable `EXIT DO` are the two sides of the rule.

## 11. Open questions

**Whether `Text` of a decimal is stable enough to fold.** It renders with `toPlainString`, which
preserves scale and is locale-independent, so it is. This is recorded because it is the one
constant operation whose output is a `STRING` derived from a `DECIMAL`, and any future change to
decimal rendering would silently change folded values.

**Whether the analysis should execute a loop rather than forget it.** A loop whose every value it
can follow is one it could run: with `n = 5`, `limit = 7` and a body of `n = n + 1`, walking the
iterations settles `n` at 7 and makes the `IF n = 7` below it decided. Everything needed is already
here — [`Constants.of`](#52-the-evaluator-reads-the-rounding-policy) evaluates in a state,
[9.2](#92-what-is-learned) updates that state through `@BubasAssigns` — and
[9.1](#91-what-is-carried) forgets instead only because forgetting is always sound.

Three things gate it. Every statement in the body must be one whose effect is declared, or the state
is a guess. Every call must be memoizable, or a value is unknown. And the walk must be bounded:
a loop the analysis can follow but that runs a billion times would hang the compiler, which is why
`maxSteps` is on `CoreContext` — the budget has to be readable from where the work happens, and that
is no longer only the interpreter.

**Whether a variadic or wildcard-typed memoizable function should fold.** Both are excluded because
their arguments cross into Java as arrays and `Value` wrappers, which the interpreter marshals and
the compiler does not. Lifting that would mean moving the marshalling somewhere both can reach, the
way `CoreArithmetic` already was. No case has asked for it.

**Where this belongs in `SPEC.md`.** The rejections are §8.3's and should move there once
implemented, leaving this file to hold the rationale.
