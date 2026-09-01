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

## The language

A `BubasLanguage` is the vocabulary: every type, function and statement a program written against
it may use. It is built with a builder and then **sealed**.

<!--INCLUDE
from: "../../bubas-doc/src/test/java/javax0/bubas/doc/expense/Expense.java"
start: '// snippet: core-language'
end: '// end snippet'
prefix: "```java"
postfix: "```"
margin: 0
_content_generated_: 548:md5:d327e7f6cfe9db574312eb7419008782
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```java
/** Stage 1: what the five-minute tutorial shows. */
static BubasLanguage.Builder core() {
    return BubasLanguage.builder()
            .install(Standard::register)
            .defineOpaqueType("Report", Report.class)
            .defineFunction("TOTAL_OF", TotalOf.class)
            .defineFunction("NOTE", Note.class)
            .defineStatement("APPROVE {expression/Report:claim}", Approve.class)
            .defineStatement("REJECT {expression/Report:claim}, {expression/STRING:reason}",
                    Reject.class);
}
```
<!--/INCLUDE-->

`install(Standard::register)` brings in declaration and assignment. Chapter 4 made the point from
the other side: those are ordinary statements, and an embedder who wants different ones simply does
not install these.

Notice this method returns a `Builder` rather than a sealed language. That is how the stages in this
book are built — `escalating()` calls `core()`, `itemised()` calls `escalating()` — so there is one
definition of `TOTAL_OF` serving six languages. In a real application you will have one language and
one method, but the pattern is worth knowing: a builder is a value you can pass around and extend,
and only sealing ends that.

## Sealing

`seal()` is where the language stops being editable and starts being usable. It is not a formality.

At `seal()` the analysis runs that proves **no two statement patterns can ever match the same line**.
Chapter 24 covers what that means and how it can fail; what matters here is when it happens. A
vocabulary with an ambiguity in it fails at startup, with a message naming both patterns — not on
the first unlucky program six weeks later.

That is the general shape of the design: expensive checks run once, at the moment the vocabulary is
fixed, so that everything downstream is cheap and certain.

A sealed language is immutable and thread-safe. Build it once, hold it for the life of the process.

## The program

`language.compile(source)` produces a `BubasProgram`: a rule that has been lexed, parsed, matched
against the vocabulary, type-checked and flow-analysed. Everything chapter 8 listed has already
happened by the time you hold one.

A `BubasProgram` is immutable and thread-safe, and it carries the language it was compiled against.
Compile a rule once and reuse it for every claim that arrives — compiling per request works, and is
simply waste.

Compilation is where a bad rule is rejected. A `BubasException` from `compile` carries the line
number, the source line, and a diagnostic. Chapter 30 is about who should see it.

## The interpreter

<!--INCLUDE
from: "../../bubas-doc/src/test/java/javax0/bubas/doc/expense/Runs.java"
start: '// snippet: interpret'
end: '// end snippet'
prefix: "```java"
postfix: "```"
margin: 0
_content_generated_: 457:md5:4c6cd7bcbe6f359c67bcf52a99ed1116
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```java
static Outcome run(BubasProgram program, Map<String, Object> arguments) {
    final var logged = new ArrayList<String>();
    final var interpreter = Interpreter.of(program);
    arguments.forEach(interpreter::argument);
    final var answer = interpreter
            .logger((level, message) -> logged.add(message))
            .run()
            .asBoolean();
    return new Outcome(program.name(), arguments, answer, List.copyOf(logged));
}
```
<!--/INCLUDE-->

An `Interpreter` holds the state of one execution: the variables, the arguments, the services, the
logger. It is deliberately cheap to make, because you make one per claim.

Three rules, and they are absolute.

**One interpreter, one run.** It is not reusable. Make another.

**One interpreter, one thread.** There is no locking in it, because it never needs any.

**Nothing is shared between runs.** Two claims decided at the same moment cannot see each other's
variables, because they are in different objects. A BUBAS program has one global scope and no
concurrency *within* a program, which means concurrency *between* programs costs nothing to reason
about.

Note also what `argument` is doing there. It checks the value against the parameter's declared type
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
