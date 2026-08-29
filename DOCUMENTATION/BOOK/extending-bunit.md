# Extending BUNIT

<!-- abstract -->
Mock commands of your own, for operations that write values rather than return them. How
interception works, what the consistency checker needs to be told about a command it has never
seen, and why that is expressed as annotations rather than an interface.
<!-- /abstract -->

---

## Most of the time you need none of this

BUNIT ships a complete set of statements — `IS MOCKED`, `RETURNS`, `SETS`, `ARGUMENT`, `RUN`, the
expectations — and they work against any vocabulary. Nothing in Part 2 was specific to expense
approval.

This chapter is for when you want a testing statement of your own: something that reads better for
your domain, or arranges a world your operations need and the standard statements express clumsily.
It is a small chapter because the mechanism is small.

## How mocking works at all

Worth understanding before extending it, because it explains why extension is possible.

BUNIT does not compile a parallel mocked language. It compiles the **real** subject, against the
**real** vocabulary, and then puts an interceptor between the running program and the handlers. When
the subject calls `TOTAL_OF`, the interceptor decides whether to answer from the test or let the
real handler run.

That choice matters more than it looks. A parallel mocked language would be a second implementation
of your vocabulary, and the two would diverge — a rule would pass its tests and fail in production
because the mocked `Report` behaved differently from the real one. Interception makes divergence
impossible: there is one language, and tests differ only in who answers.

It also means a BUNIT test is a real BUBAS program, which is why chapter 18's checker can trace its
paths, and why a test can contain an `IF` or a loop.

## A statement of your own

A BUNIT statement is an ordinary BUBAS statement — chapter 24's pattern language, unchanged — plus
annotations telling the framework what role it plays. Here is the standard `ARGUMENT`, which is as
simple as they get:

<!--INCLUDE
from: "../../bubas-bunit-commands/src/main/java/javax0/bubas/bunit/commands/Argument.java"
start:
  pattern: '@NamesParameter'
  include: true
end:
  pattern: '^\}'
  include: true
prefix: "```java"
postfix: "```"
margin: 0
_content_generated_: 314:md5:5c2b3910cc3c58801cea4f368ee516a8
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```java
@NamesParameter("name")
public final class Argument {

    public static final String PATTERN = "ARGUMENT {literal/STRING:name} IS {expression:value}";

    public void call(StatementContext ctx, String name, ExpressionArg value) {
        Mock.recorder(ctx).argument(name, value.evaluate());
    }
}
```
<!--/INCLUDE-->

The pattern is a pattern. The handler talks to the recorder. The annotation says which placeholder
holds the name of a subject parameter.

## What the annotations tell the checker

The consistency checker of chapter 18 has to reason about a statement it has never seen. It cannot
read your handler, so you tell it what the statement does:

| Annotation | Says |
|---|---|
| `@NamesTarget` | which placeholder holds the operation this statement is about |
| `@DeclaresMock` | this statement puts that target into the mocked set |
| `@SuppliesVariable` | this statement supplies a value the mocked command would have written |
| `@SuppliesResult` | this placeholder holds what a mocked function answers |
| `@MatchesArguments` | these placeholders are the arguments of the call being described |
| `@CountsArguments` | this placeholder holds a call whose own arguments are the arguments |
| `@NamesParameter` | this placeholder names a subject parameter being supplied |
| `@Act` | this is the statement that runs the subject |
| `@Expectation` | this examines what happened, so it may only appear after the act |

With those, the checker can decide whether a test is meaningful without knowing anything about your
domain. `@Expectation` alone is what makes "you cannot ask before you run" enforceable for a
statement nobody at BUNIT wrote.

## Why annotations and not an interface

The obvious design is an interface — `MockVariableSetter` with a `variableName()` method — and it is
the wrong one.

What the checker needs is **static** information: which placeholder plays which role, known before
anything runs. An interface is implemented by instance methods, which means calling a handler to
ask what it would do. Annotations are read from the class, at seal time, without constructing
anything.

There is a second reason that shows up in practice. A single statement can supply more than one
variable — chapter 24's `ROUTE` writes two — and `@SuppliesVariable` is repeatable, so that says
naturally what an interface would have needed a collection to express.

## When to write one, and when not

**Write one** when your domain has an arrangement the standard statements make awkward and your
tests keep repeating. A statement that sets up a plausible claim in one line, where four `RETURNS`
would be needed, will pay for itself across a hundred tests.

**Do not write one** to make tests shorter in general. Every custom statement is vocabulary that a
new reader must learn before they can read a test, and chapter 12's whole argument is that tests are
read by people who did not write them. The standard set is small enough to learn once and appears
in every BUBAS project; yours appears in one.

The bar is roughly: would somebody unfamiliar with your codebase understand a test using it, on
sight, without asking? If not, four ordinary lines are better than one clever one.

## What is coming

That is the vocabulary complete: types, functions, statements, descriptions, and the testing
statements to go with them. The rest of Part 3 is about running it — wiring, threading, failure,
where rules come from, and what changes when a model writes them.
