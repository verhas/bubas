# BUBAS Releases

What changed between published versions, and what an upgrade costs. For the language itself see
[`SPEC.md`](SPEC.md); for the reasoning behind a change, the commit that made it.

---

**Contents**

<!--TOC min-level: 2
max-level: 2
_content_generated_: 33:md5:a3bf92a0e848bfda7e7d94781f541f78
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
- [2.0.0](#200)
- [1.0.0](#100)
<!--/TOC-->

---

## 2.0.0

**Major, and it will not be a silent upgrade.** Rules that compiled under 1.0.0 may not compile
under this release, and applications that set the rounding policy per run will not build. Both are
intended; neither is quiet.

Thirty-one commits since 1.0.0, which was published to Maven Central on **2026-08-26** from
`693bad2` — twenty-one seconds after that commit was made. No git tag records this; one should.

### Rules that used to compile and now do not

The compiler works out what it can before a program runs, and refuses what that reveals. Every one
of these is dead code — a branch that cannot be taken, a loop body nobody will execute, arithmetic
that cannot come out — and the diagnostic names the edit that fixes it.

- **A condition the compiler can answer.** Not only `IF TRUE`, which nobody writes, but a condition
  settled by the program above it: `n = 5` four lines up decides `IF n > 10`. A `FINAL` flag read by
  an `IF` is the common case, and the answer is to make it a program parameter, where an application
  supplies it and a test can set it either way.
- **A loop that cannot run or cannot end.** `FOR line = 5 TO 1`, a step of zero, a `DO WHILE` whose
  condition is decided before the first pass, or a condition that stays true with nothing in the
  loop able to leave it.
- **A block with nothing in it** — a loop body, an `IF` or `ELSEIF` arm, or an `ELSE`.
- **Constant arithmetic that cannot succeed**, wherever it is written and whether or not anything
  could reach the line: overflow, division or `MOD` by zero.
- **`TO_INTEGER("twelve")` and its like.** These functions now answer at compile time, so text that
  is not a number is a compile error rather than a failure on the run that eventually reaches it.

Loops are followed as well as read. Where the compiler holds every value a loop turns on, it runs
the loop while compiling — so `n = 5` above a loop counting to seven settles the `IF n = 7` below
it. A loop whose values come from a parameter or an operation is not followed, and everything after
it stays a question.

### Java that used to build and now does not

- **`Interpreter.mathContext(MathContext)` is gone.** The rounding policy is set on
  `BubasLanguage.Builder.mathContext(...)` and sealed with the vocabulary. One compiled program now
  divides identically in every run — including in a BUNIT test and in the production run of the rule
  it tests, which previously could differ with nothing anywhere comparing them.
- **`Context` is split.** `CoreContext` carries `mathContext`, `log`, `debug`, `error`, `maxSteps`
  and `maxArrayLength`; `Context` extends it and adds the services. Callers are unaffected —
  everything is still reachable through `Context` — but anything *implementing* `Context`, a test
  double most likely, must supply the two new methods.
- **`ToInteger.call` and `ToDecimal.call` take a `CoreContext`.** They are
  `@BubasMemoizable` (below), and a memoizable function may not reach an application.

### Results that may differ without any error

The rounding policy moved from the run to the language. An embedder who varied `MathContext`
per interpreter will find every run using the language's policy instead. Nothing reports this; the
numbers simply change.

### New

**Two annotations a vocabulary uses to tell the compiler what it may assume.** Both optional, and a
vocabulary that declares neither behaves exactly as before.

- **`@BubasAssigns(target, value)`** — this command copies one placeholder into another. It is what
  lets the compiler know that after `n = 5` the variable holds 5. Repeatable, because one statement
  may fill several variables and may declare only some of them. `Assign`, `DeclareInitialized` and
  `DeclareFinal` carry it, which is how an embedder's own assignment syntax joins in.
- **`@BubasMemoizable`** — this function answers the same way for the same arguments, so a call on
  known arguments may be answered while compiling. Its method takes a `CoreContext`, which has no
  `service` on it, so reaching an application does not compile and `seal()` refuses a memoizable
  function declaring otherwise. It may log — the line is discarded — and it may refuse, which
  becomes a compile error at the line of the call.

**Four limits, two on the run and two on the compilation.**

| | |
|---|---|
| `Interpreter.maxSteps(long)` | statements and loop passes one run may take |
| `Interpreter.maxArrayLength(int)` | the largest array a command may allocate |
| `BubasLanguage.Builder.maxSteps(long)` | how hard the compiler tries to follow a loop |
| `BubasLanguage.Builder.maxLoops(long)` | how many passes a loop may take |

All default to unlimited except the compiler's effort, which defaults to 100,000. The two named
`maxSteps` do different jobs and the pair on the builder are opposites: the compiler's effort never
changes which programs compile, while `maxLoops` is a policy about programs and does.

Two things worth knowing before relying on either. The array limit is enforced by the command that
allocates, through `CoreContext.maxArrayLength()` — nothing can enforce it on a command's behalf,
because an array that has reached a variable is memory already spent, so **a vocabulary with its own
array-making statement must ask**. And `maxLoops` only ever sees loops whose values are all written
in the rule, which are the ones a reviewer could have counted by reading; a loop over a list whose
length arrives from a service is invisible to it.

**Constant arithmetic is evaluated while compiling**, decimal division included, which is possible
only because the rounding policy is now fixed before a program exists.

### Fixed

- **BUNIT could not test a program handed an opaque value.** A mocked command's opaque argument
  arrived as something the matchers could not compare, so a whole shape of test was unwritable.
  `Interpreter.argument(String, Value)` is new alongside the fix.
- **The vocabulary export omitted an opaque type described on its own class**, reporting a hole
  where the description was sitting in plain sight. `defineOpaqueTypeVia` is the escape route for a
  type whose class cannot carry an annotation — `java.time.LocalDate` and its like — not the ordinary
  way to describe one.
- **A decimal division under `MathContext.UNLIMITED`** whose quotient has no finite form threw a bare
  `ArithmeticException` out of `run()`, past everything that would have named a line. It is now an
  ordinary BUBAS failure, reported against the line that divided.

### Documentation

Most of it is new, and none of it existed when 1.0.0 was published.

- **The book** — 33 chapters in three parts, language, BUNIT, and embedding, with a PDF build
  (`generate_pdf.py`) whose output is a function of the chapters rather than of the clock.
- **Two tutorials**, five minutes and fifteen.
- **Every example is compiled.** Fragments are pulled from sources the build compiles and outputs
  from runs the build executes, so a reworded diagnostic or a changed total shows up as a
  documentation diff on the build that caused it. `DOCUMENTATION/AUTHORING.md` records the rules.
- **[`CONSTANTS.md`](CONSTANTS.md)** — what the compiler works out before a program runs, and why
  each rejection is a rejection.
- **[`CHECKS.md`](CHECKS.md)** — a design, not an implementation: an SPI for domain-specific checks
  an embedder could register. Nothing in this release implements it.

### Upgrading

1. Move `Interpreter.mathContext(...)` to the builder. If different runs genuinely needed different
   rounding, they now need different languages.
2. Compile your rules. What breaks is dead code; the diagnostic says which edit fixes it.
3. A `FINAL` flag read by an `IF` becomes a program parameter.
4. Consider `@BubasAssigns` on your own assignment-shaped commands and `@BubasMemoizable` on
   functions that answer from their arguments alone. Neither is required, and leaving both off costs
   only the compiler's ability to see through your vocabulary.

---

## 1.0.0

The first published release: the language, BUNIT, the vocabulary export, and the book.
