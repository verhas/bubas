# Three objects

<!-- abstract -->
The whole architecture in one chapter: a language that is defined once and sealed, a program that
is compiled once and reused, and an interpreter that is cheap, single-use and single-threaded.
What each costs, what each is safe to share, and why sealing exists.
<!-- /abstract -->

---

## Welcome to the Java

Parts 1 and 2 contained none, deliberately: everything up to here can be read by the person who
owns the rules, and defining a vocabulary is a different job. This is that job.

It rests on three objects, and almost every question about embedding BUBAS is a question about
which of the three you are holding.

| Object | Made | Cost | Shared |
|---|---|---|---|
| `BubasLanguage` | once, at startup | expensive | freely, across threads |
| `BubasProgram` | once per rule text | moderate | freely, across threads |
| `Interpreter` | once per execution | trivial | never |

## One whole example first

Everything below is one embedding, small enough to read at once. It has a single type, a single
question, a single instruction — and it is complete: there is nothing left out to make it fit on
the page.

## The language

A `BubasLanguage` is the vocabulary: every type, function and statement a program written against
it may use. It is built with a builder and then **sealed**.

<!--INCLUDE
from: "../../bubas-doc/src/test/java/javax0/bubas/doc/expense/FirstLanguage.java"
start: '// snippet: first-language'
end: '// end snippet'
prefix: "```java"
postfix: "```"
margin: 0
_content_generated_: 375:md5:faf6d7c5cca792a754163b397223c1d6
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```java
/** Built once, at startup, and held for the life of the process. */
static final BubasLanguage LANGUAGE = BubasLanguage.builder()
        .install(Standard::register)
        .defineOpaqueType("Claim", Claim.class)
        .defineFunction("TOTAL_OF", TotalOf.class)
        .defineStatement("APPROVE {expression/Claim:expense}", Approve.class)
        .seal();
```
<!--/INCLUDE-->

`install(Standard::register)` brings in declaration and assignment. Chapter 4 made the point from
the other side: those are ordinary statements shipped in a module, and an embedder who wants
different ones simply does not install these.

Everything after it is this application's own. `Claim` is a Java class of yours; `TOTAL_OF` and
`APPROVE` are Java classes of yours. The next three chapters are about writing them.

Build this once, in startup, and hold it for the life of the process. It is immutable and
thread-safe once sealed.

## Sealing

`seal()` is where the language stops being editable and starts being usable. It is not a formality.

At `seal()` the analysis runs that proves **no two statement patterns can ever match the same
line**. Chapter 24 covers what that means and how it can fail; what matters here is when it
happens. A vocabulary with an ambiguity in it fails at startup, with a message naming both
patterns — not on the first unlucky program six weeks later.

That is the general shape of the design: expensive checks run once, at the moment the vocabulary is
fixed, so that everything downstream is cheap and certain.

## The program

This is the rule the example runs — an ordinary text file, kept wherever your application keeps
them:

<!--INCLUDE
from: "../../bubas-doc/src/test/resources/programs/first-rule.bu"
prefix: "```"
postfix: "```"
_content_generated_: 238:md5:259e2d50a811783d714e8cfab04f6610
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```
PROGRAM ApproveSmallClaim(expense Claim, limit DECIMAL) RETURNS BOOLEAN
    DECLARE total DECIMAL

    total = TOTAL_OF(expense)

    IF total > limit THEN
        RETURN FALSE
    END IF

    APPROVE expense
    RETURN TRUE
END
```
<!--/INCLUDE-->

<!--INCLUDE
from: "../../bubas-doc/src/test/java/javax0/bubas/doc/expense/FirstLanguage.java"
start: '// snippet: first-compile'
end: '// end snippet'
prefix: "```java"
postfix: "```"
margin: 0
_content_generated_: 175:md5:a0b8f972a1ceb1f006aa6363e9d0739e
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```java
/** Compiled once per rule text, then reused for every claim that arrives. */
static final BubasProgram PROGRAM = LANGUAGE.compile(Runs.source("first-rule.bu"));
```
<!--/INCLUDE-->

`Runs.source` there is nothing but a file read, spelt however suits you. `compile` produces a
`BubasProgram`: a rule that has been lexed, parsed, matched against the
vocabulary, type-checked and flow-analysed. Everything chapter 8 listed has already happened by the
time you hold one.

A `BubasProgram` is immutable and thread-safe, and it carries the language it was compiled against.
Compile a rule once and reuse it for every claim that arrives — compiling per request works, and is
simply waste.

Compilation is where a bad rule is rejected. A `BubasException` from `compile` carries the line
number, the source line, and a diagnostic. Chapter 30 is about who should see it.

## The interpreter

<!--INCLUDE
from: "../../bubas-doc/src/test/java/javax0/bubas/doc/expense/FirstLanguage.java"
start: '// snippet: first-run'
end: '// end snippet'
prefix: "```java"
postfix: "```"
margin: 0
_content_generated_: 304:md5:9445a9106f223188950cc2778f13d24e
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```java
/** One interpreter per decision. Cheap to make, used once, thrown away. */
static boolean decide(Claim claim, BigDecimal limit) {
    return Interpreter.of(PROGRAM)
            .argument("expense", claim)
            .argument("limit", limit)
            .run()
            .asBoolean();
}
```
<!--/INCLUDE-->

An `Interpreter` holds the state of one execution: the variables, the arguments, and whatever the
application supplies for this run. It is deliberately cheap to make, because you make one per
claim.

Three rules, and they are absolute.

**One interpreter, one run.** It is not reusable. Make another.

**One interpreter, one thread.** There is no locking in it, because it never needs any.

**Nothing is shared between runs.** Two claims decided at the same moment cannot see each other's
variables, because they are in different objects. A BUBAS program has one global scope and no
concurrency *within* a program, which means concurrency *between* programs costs nothing to reason
about.

Note what `argument` is doing. It checks the value against the parameter's declared type
immediately, so a wiring mistake surfaces before the rule runs rather than at the first use.

## Why it is three and not one

The obvious design is one object: hand it a source string and some arguments, get an answer. It
would be a smaller API and a worse one.

Splitting them puts each cost where it belongs. The expensive work — the overlap analysis, the type
checking, the flow analysis — happens once per vocabulary and once per rule. The per-claim work is
allocating a small object and walking a tree.

It also puts each *failure* where it belongs. A vocabulary that is ambiguous fails at startup. A
rule that does not compile fails when it is saved, in front of whoever wrote it. A rule that divides
by zero fails on the claim that did it. Three failure modes, three moments, three audiences —
chapter 30's subject.

And it makes the sharing rules simple enough to hold in your head, which the table at the top is.

## What is coming

The next three chapters are the vocabulary itself: types, functions, and the pattern language that
makes statements. They are the design work that decides how good Parts 1 and 2 feel to the people
living in them.
