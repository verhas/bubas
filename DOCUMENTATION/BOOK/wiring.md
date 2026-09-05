# Wiring

<!-- abstract -->
Services, logging, configuration, and the context handlers are given. Keeping handlers thin, and
where the application's real work should sit so that the vocabulary stays a vocabulary.
<!-- /abstract -->

---

## Handlers must not own anything

A handler class is constructed by BUBAS, not by you. It has no constructor you control, no fields
you set, and no way to be injected into.

That is not an oversight, and working around it is the first mistake people make. A handler that
held a database connection would be a handler with a lifecycle, and the whole point of chapter 21's
three objects is that only one of them has a lifecycle worth thinking about.

So a handler reaches out instead:

<!--INCLUDE
from: "../../bubas-doc/src/test/java/javax0/bubas/doc/expense/Wiring.java"
start:
  pattern: '// snippet: thin-handler'
  include: true
end:
  pattern: '// end snippet'
  include: false
prefix: "```java"
postfix: "```"
margin: 0
_content_generated_: 288:md5:f3b01b383ae376c271374b3a0c767d49
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```java
// snippet: thin-handler
/** A handler translates and delegates. It owns nothing and has no lifecycle. */
public static final class TotalOf {
    public BigDecimal call(Context ctx, Expense.Report claim) {
        return ctx.service(ClaimStore.class).totalOf(claim);
    }
}
```
<!--/INCLUDE-->

`Context.service(Class)` returns whatever the application registered on the interpreter for this
run. There is a qualified form, `service(Class, String)`, for when one type has several instances —
two databases, two clients.

## Registering them

Services go on the interpreter, which means **per run**:

<!--INCLUDE
from: "../../bubas-doc/src/test/java/javax0/bubas/doc/expense/Wiring.java"
start:
  pattern: '// snippet: per-run-wiring'
  include: true
end:
  pattern: '// end snippet'
  include: false
prefix: "```java"
postfix: "```"
margin: 0
_content_generated_: 519:md5:8398df09b04e3dcb3cb21029b69b6828
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```java
// snippet: per-run-wiring
/** Services, arguments and the logger are supplied per run, never per language. */
static Value decide(BubasProgram program, ClaimStore store, Expense.Report claim,
                    BigDecimal limit, java.util.function.BiConsumer<String, String> auditLog) {
    return Interpreter.of(program)
            .registerService(ClaimStore.class, store)
            .argument("claim", claim)
            .argument("limit", limit)
            .logger(auditLog)
            .run();
}
```
<!--/INCLUDE-->

Per run rather than per language, and that is deliberate. It means a request-scoped transaction, a
tenant-specific store or a test double can be supplied for one execution without touching anything
shared. The language and the program stay immutable; everything that varies varies here.

The cost is that a service forgotten is discovered when a handler asks for it. Registering the same
set in one place — a small factory that turns a claim into a configured interpreter — is worth doing
on the first day rather than the fortieth.

## What a run is allowed to spend

Two things an application can cap, and both belong on the interpreter because both vary by caller —
a nightly batch and a request handler do not deserve the same budget:

```java
Interpreter.of(program)
        .maxSteps(1_000_000)
        .maxArrayLength(100_000)
```

`maxSteps` counts statements executed and loop passes taken. Chapter 8's compiler already refuses
the loops it can *prove* never end, but a loop that stops when a service says so is not one of those,
and the service can be wrong. `maxArrayLength` caps what a single `DECLARE items[n] Order` may bring
into existence, which matters because `n` is an expression and a wrong figure from upstream is an
allocation nobody intended.

Both are unlimited unless you say otherwise, and a run inside its budget cannot tell they are there.
Set them anyway. A rule is written by somebody who is not thinking about budgets, which is the
arrangement this whole book argues for, and it only works if somebody else is.

One obligation comes with the second. **A command that allocates has to ask** — the array limit is
readable through `CoreContext.maxArrayLength()`, and the standard `DECLARE` consults it before taking
the memory. The runtime cannot do this for you: an array that has arrived in a variable is already
allocated, so a check there would catch the mistake after paying for it. If chapter 24's vocabulary
grows a statement that makes an array, it asks first.

## What a compilation is allowed to spend

Two more settings, on the language rather than the interpreter, and they look far more alike than
they are:

```java
BubasLanguage.builder()
        .maxSteps(100_000)      // how hard the compiler tries
        .maxLoops(1_000_000)    // how long a loop a rule may contain
```

Chapter 7 mentioned that the compiler works a loop out when it can see every value the loop turns
on. `maxSteps` is how long it keeps trying before giving up on a loop. **Giving up changes nothing
about what compiles** — it just means the compiler stops knowing what that loop left behind, exactly
as if the values had come from outside. Leave it alone unless a compilation is slow.

`maxLoops` is the opposite kind of setting. It is a rule about rules: where the compiler can follow
a loop, it knows how many passes the loop takes, and refuses the rule when that is more than you
allow. Unlimited unless you set it.

Be clear-eyed about what it buys. It sees only loops whose every value is written in the rule
itself — the ones a reviewer could have counted by reading. A loop over a list whose length arrives
from a service is invisible to it, and `Interpreter.maxSteps` from the previous section is the only
thing that bounds that one. So it catches the careless loop and misses the dangerous one, which is
still worth having as long as nobody mistakes it for protection.

## Rounding, which is not per run

One setting deliberately does not go on the interpreter:

<!--INCLUDE
from: "../../bubas-doc/src/test/java/javax0/bubas/doc/expense/Wiring.java"
start:
  pattern: '// snippet: rounding'
  include: true
end:
  pattern: '// end snippet'
  include: false
prefix: "```java"
postfix: "```"
margin: 0
_content_generated_: 395:md5:a4aaa342840108f0c131e3f05b3bffc4
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```java
// snippet: rounding
/**
 * The rounding policy belongs to the language, not to the run. Sixteen significant digits and
 * banker's rounding, decided once, at startup, for every rule this language ever compiles.
 */
static BubasLanguage roundedToTheCent() {
    return Expense.approving()
            .mathContext(new MathContext(16, RoundingMode.HALF_EVEN))
            .seal();
}
```
<!--/INCLUDE-->

Chapter 3 said the application draws the line on division and every rule follows suit. This is where
it draws it: on the builder, sealed with everything else, before a single rule is compiled.

It could have been per run, and was once. The reason it is not is worth a paragraph, because it is
the same reason this chapter keeps insisting on immutability. A rounding policy on the interpreter
means the same rule can settle money one way in your test and another way in production, with
nothing anywhere comparing the two — and the BUNIT suite of chapter 12 onwards takes the language,
so it would have inherited the wrong one silently. Rounding is an accounting decision, one rule set
answers to one, and it belongs where the vocabulary is decided rather than where a request is
served.

The practical consequence is that a rule needing different rounding for one calculation asks for it
explicitly, with an operation that says so. That reads better in a rule than an invisible global,
which is the trade this book keeps making.

## Logging

`logger(BiConsumer<String, String>)` receives every `ctx.log(level, message)` a handler makes.

Two things worth deciding early, because they are hard to change later.

**The log is the rule's output, not diagnostics.** In every example in this book, `APPROVE` and
`REJECT` record what they decided by logging it. That is the decision trail an auditor reads, and it
should go somewhere durable and queryable, not to a rolling text file.

**Levels are yours.** BUBAS passes the string through untouched. This book uses `DECISION`, `NOTE`
and `RECORD` because they are what the domain calls them, and nothing forces `INFO`/`WARN`.

## Where the real work goes

The recurring failure in an embedded language is a vocabulary that quietly becomes the application.

A thin handler translates and delegates. It converts BUBAS values into domain calls, calls
something that already existed, and converts back. If a handler is more than a few lines, ask what
it is doing that a domain service should have been doing.

Two smells worth naming:

**A handler that reads several services and combines them.** That combination is domain logic
living in a translation layer, where it is hard to test and impossible to reuse from anywhere but
BUBAS. It belongs in a service.

**A handler that branches on the values it was given.** A decision inside a handler is a decision
that has left the rule — chapter 23's warning about `CHECK_EXPENSE_POLICY`, in miniature. Sometimes
it is legitimate (`ANOMALY_SCORE_OF` has to decide something), but it is always worth a second look.

The test is the same one chapter 23 gave: *does this encode a decision somebody could disagree
with?* If yes, it wants to be in the rule or behind a named service, not in the glue.

## Configuration

Thresholds, caps and limits are the interesting case, and the answer runs against instinct.

**Do not configure them.** A cap that lives in a properties file has left the rule, and everything
this book argues for has been given up quietly: it is no longer visible to the reviewer, no longer
part of the artefact under review, no longer version-controlled with the logic it governs.

Chapter 6's rule declares `ceiling` as a `FINAL` in the program, where a finance manager reads it.
That is the right place. If a number varies by tenant or by period, it becomes a **parameter** —
which is what `limit` is throughout this book — and the application supplies it per run.

One caveat, and chapter 8 is where it bites. A `FINAL` is fine as a *figure* — a cap compared
against a total nobody has worked out yet is a real comparison. It is not fine as a *switch*: a
`FINAL` flag read by an `IF` is a test the compiler can answer, and it is refused. Anything the
rule's behaviour turns on is a parameter, not a constant.

What genuinely belongs in configuration is infrastructure: endpoints, credentials, timeouts, which
model version chapter 25 told you to pin. Nothing a rule-writer would recognise.

## The shape it settles into

Most applications end up with the same four pieces, and it is worth aiming at them directly:

- **One place that builds and seals the language**, at startup, once.
- **One place that compiles rules**, whenever the rule text changes, holding the compiled programs.
- **A small factory** that turns a request into a configured interpreter with its services and
  logger.
- **Handlers that do nothing but translate.**

Everything else is your application, unchanged and unaware that a language is involved. That is the
sign it is wired correctly: the vocabulary is a thin edge on a system that would still make sense
without it.
