# Defining functions

<!-- abstract -->
Operations that answer questions: handler classes, the context they are given, variable arity, and
wildcard parameters. Which work belongs behind a function, and the signal that you are about to put
business logic somewhere your experts cannot see it.
<!-- /abstract -->

---

## The shape

A function is a class with a `call` method:

<!--INCLUDE
from: "../../bubas-doc/src/test/java/javax0/bubas/doc/expense/Expense.java"
start: '// snippet: total-of'
end: '// end snippet'
prefix: "```java"
postfix: "```"
margin: 0
_content_generated_: 205:md5:af35436ff2553b576098511e17c06623
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```java
@BubasDescription("What a claim comes to in total, in euro.")
public static final class TotalOf {
    public BigDecimal call(Context ctx, Report claim) {
        return claim.total();
    }
}
```
<!--/INCLUDE-->

Registered with `.defineFunction("TOTAL_OF", TotalOf.class)`, and that is the whole contract. No
interface, no base class, no annotations beyond the description.

BUBAS reads the signature to work out the vocabulary entry. `Context` is always first and is not a
BUBAS parameter. The rest map by their Java types: `long` is `INTEGER`, `BigDecimal` is `DECIMAL`,
`String` is `STRING`, `boolean` is `BOOLEAN`, and a registered class is its opaque type. The return
type becomes what the function answers, and `void` makes it a statement-form call — chapter 4's
`NOTE`.

**Parameter names matter.** They appear in the vocabulary document and in every type error:
`AMOUNT_OF takes Item for 'line', but was given Report`. Name them for the domain, not `arg0`.

## What the context gives you

`Context` is how a handler reaches the world outside itself: `service(Class)` for whatever the
application registered, `log`, `debug`, `error`, and `mathContext()` for the division rules of
chapter 3.

It comes in two pieces. `CoreContext` has everything but the services — the rounding policy, the
log, and `error` — and `Context` adds `service`. Take `Context` unless you have a reason not to; the
next section is the reason.

What it does not give you is access to the program. A handler cannot read the rule's variables,
cannot know which line called it, and cannot change what happens next. It answers the question it
was asked. That restraint is what makes chapter 21's threading story simple.

## Functions the compiler may call

A function is a black box to the compiler. It might read a database, a clock or a feature flag, and
nothing about `long call(Context, String)` says which — so a call is never worked out early, however
arithmetical it looks.

A function that genuinely answers from its arguments alone can say so, and the standard module's
`TO_INTEGER` does:

<!--INCLUDE
from: "../../bubas-support/src/main/java/javax0/bubas/support/ToInteger.java"
start:
  pattern: '^@BubasMemoizable'
  include: true
end:
  pattern: '^\}'
  include: false
prefix: "```java"
postfix: "```"
margin: 0
_content_generated_: 419:md5:e62506493306ad0e18018682afa4f635
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```java
@BubasMemoizable
public final class ToInteger {

    public static final String NAME = "TO_INTEGER";

    public long call(CoreContext ctx, String s) {
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            ctx.error("'" + s + "' is not an INTEGER");
            throw new IllegalStateException("unreachable: error() throws", e);
        }
    }
```
<!--/INCLUDE-->

The compiler may then call it while compiling, when every argument is a value it already knows.
`TO_INTEGER("5")` becomes 5, and — since chapter 8's refusals follow values — a test on the result
is a test with an answer, and refused.

Three things are worth knowing before you reach for it.

**Half of it is not a promise at all.** Look at the first parameter: `CoreContext`, not `Context`.
It carries the rounding policy, the log and `error`, and it has no `service` method on it — so a
memoizable function that tries to reach the application does not compile, in Java, in your IDE,
anything else happens. Writing `Context` there is refused when the language is sealed, at startup,
rather than on whichever compilation first happens to know all the arguments.

**The other half is a promise, and nothing checks it.** A clock read through a static field, a file,
a cached lookup: those the compiler cannot see. A function that declares this falsely will answer at
compile time with a value a run would not have produced, and nothing will say so. When in doubt
leave it off; all you lose is a fold.

**It only ever applies to plain values.** Every parameter and the result must be `INTEGER`,
`DECIMAL`, `STRING` or `BOOLEAN`. An array is a store, an opaque value is a Java object, and neither
is something a compiled program can hold, so a function touching one is never called early however
pure it is. `TOTAL_OF(claim)` takes a `Report` and would not qualify — which is just as well, since
it reads one.

**Refusing is allowed, and becomes a compile error.** `ctx.error` during a compile-time call fails
the compilation at the line of the call. That follows from the promise rather than contradicting it:
a function that answers the same way every time and refuses these arguments now would refuse them on
every run, so the compiler is giving the same answer earlier.

Logging is allowed too, and the line is thrown away. It decides nothing, so it is no reason to
decline the fold — but the run that would have written it may happen a thousand times or never, and
compiling is not one of them. Do not put anything in such a function's log that you expect to
count.

## Any number of arguments

<!--INCLUDE
from: "../../bubas-doc/src/test/java/javax0/bubas/doc/expense/Expense.java"
start: '// snippet: notify'
end: '// end snippet'
prefix: "```java"
postfix: "```"
margin: 0
_content_generated_: 442:md5:f5c9990a69169664a88c5b5c36f69e62
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```java
/**
 * Variadic: BUBAS sees {@code NOTIFY(people STRING...)} and calls it with a spread list, never
 * with an array. An embedder who wants an array declares an array parameter instead.
 */
@BubasDescription("Tells one or more people about a claim. Answers nothing.")
public static final class Notify {
    public void call(Context ctx, String... people) {
        ctx.log("NOTIFY", "told " + String.join(", ", people));
    }
}
```
<!--/INCLUDE-->

A Java varargs parameter becomes a variadic operation. A rule calls it with as many as it likes — `NOTIFY "the claimant"` on one path, and
`NOTIFY approver, "the claimant", "the finance inbox"` on another, both in the program above.

Spread only. `NOTIFY(people)` where `people` is an array does not compile, even though Java would
accept it, because allowing both would revive an ambiguity nobody wants. An embedder who genuinely
wants an array declares an array parameter instead.

Every argument is type-checked against the element type, so the error names the position that was
wrong rather than reporting a count.

## Arguments of any type

<!--INCLUDE
from: "../../bubas-doc/src/test/java/javax0/bubas/doc/expense/Expense.java"
start: '// snippet: record-any'
end: '// end snippet'
prefix: "```java"
postfix: "```"
margin: 0
_content_generated_: 533:md5:0b4eccd73a75e31fb1a1cf60f0d06efd
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```java
/**
 * A wildcard parameter: BUBAS sees {@code RECORD(label STRING, value ANY)} and accepts any
 * type in the second position. The handler asks the value what it is rather than being told by
 * its own signature.
 */
@BubasDescription("Writes a labelled value into the decision record, whatever kind of value it is.")
public static final class Record {
    public void call(Context ctx, String label, Value value) {
        ctx.log("RECORD", label + " = " + value.as(Object.class) + " (" + value.type() + ")");
    }
}
```
<!--/INCLUDE-->

Declaring a parameter as `Value` makes it `ANY`: it accepts every type, and the handler asks the
value what it is rather than being told by its own signature.

<!--INCLUDE
from: "../../bubas-doc/target/doc-outputs/stage7-telling.txt"
prefix: "```"
postfix: "```"
_content_generated_: 483:md5:d8327a30aa93f9f9ff50a90130bdc6a8
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```
TellPeople(claim = report 1 (Alice), limit = 200.00)
    total = 128.40 (DECIMAL)
    over the limit = false (BOOLEAN)
    told the claimant
    approved report 1 (Alice) for 128.40
    => TRUE

TellPeople(claim = report 2 (Erin), limit = 200.00)
    total = 450.00 (DECIMAL)
    over the limit = true (BOOLEAN)
    budget left = 2500.00 (DECIMAL)
    told the line manager, the claimant, the finance inbox
    escalated report 2 (Erin) — over the 200.00 limit
    => FALSE
```
<!--/INCLUDE-->

A decimal and a boolean went into the same operation, and each rendered itself.

`ANY` is for **parameters only, never returns**. An operation that answers `ANY` would push the
type question into the rule, where there is nothing to do about it — a rule cannot ask a value what
it is. Answer a real type, or answer text.

Use it sparingly. Logging, recording and rendering are the honest cases: operations whose whole job
is to accept whatever they are given. An operation that behaves *differently* depending on the type
it receives is a design that wants to be several operations.

## What belongs behind a function

This is the judgement that decides how good the language feels to use.

**Anything the domain has a name for.** If people say "what did this claim come to", there should be
an operation called that. The test is whether the phrase exists before you write the code.

**Anything computational.** Route optimisation, currency conversion, a scoring model, a date
calculation. Chapter 9 made the argument from the rule-writer's side: wanting a data structure means
you have left business logic. This is the other half of it — that work belongs here, where it can be
tested properly in Java.

**Anything that would otherwise be repeated.** If three rules compute the same thing from the same
two operations, that computation is a piece of domain knowledge living in the wrong place. Promote
it.

## The signal you are hiding the rule

The opposite mistake matters more, and it is easy to make while feeling productive.

An operation named `CHECK_EXPENSE_POLICY(claim) -> BOOLEAN` would work. It would also move the
entire policy into Java, where the person who owns it cannot read it, and leave the BUBAS rule as a
single line that calls it. Everything this book argues for would have been given up in one
registration.

The test is: **does this operation encode a decision somebody could disagree with?**

`TOTAL_OF` does not — adding up lines is arithmetic. `ANOMALY_SCORE_OF` returns a score and leaves
the decision in the rule, which is chapter 25's whole subject. `CHECK_EXPENSE_POLICY` is a decision
wearing a function's clothes.

When you find yourself about to expose one, the question to take back to whoever asked is usually
"which part of this is the policy?" — and the answer is what stays in the rule.

## What is coming

Functions answer. The next chapter is the pattern language: how a class becomes a statement, with
keywords in the middle and variables it can write into.
