# BUBAS Constant Evaluation — Specification

**Version** 1.0 · **Status** Design agreed; not implemented. See [`SPEC.md`](SPEC.md) for the
language itself and [`CHECKS.md`](CHECKS.md) for domain checks.

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
_content_generated_: 474:md5:196aa579f05c39a9df6c0da34cdb86fb
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
- [9. Constant propagation](#9-constant-propagation)
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
[9](#9-constant-propagation).

It is not dead-code *elimination*. Nothing is deleted. Unreachable code is reported, and the author
deletes it. A compiler that silently removed the branch would be hiding the mistake it just found.

## 2. Position in the pipeline

Constant evaluation needs types: knowing that `1 + 2` is integer addition rather than concatenation
is what makes it evaluable. Types are computed in `Lowering` and nowhere else.

Flow analysis today runs **before** lowering, on the untyped tree, and produces the symbol table
lowering consumes:

```java
final var symbols = FlowAnalyser.check(program, this);
return new BubasProgram(this, Lowering.lower(program, this, symbols));
```

Since the new rejections belong with the dead-code rejections flow analysis already makes
([`SPEC.md` §8.3](SPEC.md#83-rejected-at-compile-time)), and those now depend on constant values,
the order inverts:

1. lex, parse, match statements against patterns
2. **symbol collection** — split out of `FlowAnalyser`
3. lowering to the core tree
4. **constant evaluation**
5. definite assignment and reachability, over the core tree
6. domain checks ([`CHECKS.md`](CHECKS.md))
7. `BubasProgram` returned

> **Rationale (not normative).** The alternative is a second type inference over the AST so that
> constants can be folded before flow analysis. That is precisely the divergence the core tree
> exists to prevent — `Lowering`'s own documentation says a separate type checker would compute
> the knowledge, throw it away and leave lowering to derive it again.
>
> The inversion pays for itself elsewhere. `FlowAnalyser` keeps a stack of enclosing loops to
> validate `EXIT FOR` and `EXIT DO`, and `Lowering` separately assigns loop identities and
> resolves `Break(loopId)`. Analysing the core tree leaves one mechanism where there were two.
>
> Lowering does not read flow results — only the symbol table — so splitting symbol collection out
> is the whole cost of the move.

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
| `Call` | no | see [3.1](#31-functions-are-never-constant) |

### 3.1. Functions are never constant

A function may read the host, the clock or a database, and BUBAS has no way to say that one does
not. Purity is not expressible, so it is not assumed. `LENGTH("abc")` stays a call.

> **Rationale (not normative).** A purity declaration is addable later — it would be an argument to
> `defineFunction`, not a change to this pass. It is left out because the embedder would have to be
> right about it, and being wrong would produce a program that computes a stale answer forever.

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
condition that keeps the loop running with **no reachable `EXIT`** for it — which is exactly the
"provably non-terminating loop" §8.3 already names and could not previously detect.

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

Because a constant condition is an error, no arm is ever known-unreachable *because of* its
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

## 9. Constant propagation

A `FINAL` variable initialised with a constant expression is itself constant, and propagating it
would extend every rule in [6](#6-rejections) to reach through such variables. It is a separate
development, and a wanted one.

It is not a hazard for [6.1](#61-a-constant-branch-condition), because the idiom it would newly
reject is one BUBAS does not want:

```basic
DECLARE debug BOOLEAN FINAL = FALSE
...
IF debug THEN
```

A flag a program's behaviour depends on belongs in the program's parameters, where the embedder
supplies it and a BUNIT test can set it — at which point it is not constant and the code is not
dead. The rule therefore keys on **any provably constant condition**, not merely a literal one, and
propagation may be added without revisiting this document.

## 10. Testing

The evaluator and the interpreter must agree, and after [5](#5-evaluation) they share their
primitives, so what remains to test is the pass itself: every program in the suite, run with
evaluation on and off, must produce identical results and identical failures. Because the rounding
policy is now a property of the language, "under several `MathContext` settings" means compiled
against several languages that differ only there — which is also the cheapest test that the policy
really does travel with the program.

Each rejection in [6](#6-rejections) needs a program that triggers it and a neighbouring one that
does not — particularly [6.2](#62-a-loop-that-cannot-end), where `DO WHILE TRUE` with and without a
reachable `EXIT DO` are the two sides of the rule.

## 11. Open questions

**Whether `Text` of a decimal is stable enough to fold.** It renders with `toPlainString`, which
preserves scale and is locale-independent, so it is. This is recorded because it is the one
constant operation whose output is a `STRING` derived from a `DECIMAL`, and any future change to
decimal rendering would silently change folded values.

**Whether a function may declare itself pure.** See [3.1](#31-functions-are-never-constant).

**Where this belongs in `SPEC.md`.** The rejections are §8.3's and should move there once
implemented, leaving this file to hold the rationale.
