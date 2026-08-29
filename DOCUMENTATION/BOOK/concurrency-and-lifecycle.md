# Concurrency and lifecycle

<!-- abstract -->
Seal once, compile once, interpret per request. What is shared safely between threads, what must
never be, and how this maps onto a web application, a batch job, and a queue consumer.
<!-- /abstract -->

---

## The whole rule

| Object | Made | Shared between threads |
|---|---|---|
| `BubasLanguage` | once, at startup | yes, freely |
| `BubasProgram` | once per rule text | yes, freely |
| `Interpreter` | once per execution | **never** |

That is the entire concurrency story, and it holds because of a decision made much earlier: a BUBAS
program has one global scope, no local variables, and no concurrency within itself. An interpreter
therefore owns all the state of one run, and two runs cannot see each other because they are two
objects.

There is no lock anywhere in the interpreter, because there is nothing to lock.

## Sealing at startup

Build the language once, in application startup, and hold it for the life of the process.

This is where chapter 24's overlap analysis runs, so an ambiguous vocabulary is a **startup
failure** — the process does not come up, with a message naming both patterns. That is the
behaviour you want: better a deployment that fails immediately than one that succeeds and behaves
unpredictably on the first program that happens to be ambiguous.

Do not seal lazily, and do not seal per request. It is expensive by design, once.

## Compiling when the rule changes

A `BubasProgram` is expensive relative to a run and cheap relative to startup. Compile when the
rule text changes and hold the result.

For rules that ship with the application, compile at startup beside the sealing. For rules that
people edit at runtime — chapter 31's subject — compile on save, keep the compiled program in a map
keyed by whatever identifies the rule, and replace the entry when the text changes.

Replacing an entry is safe without coordination. A `BubasProgram` is immutable, so a thread that
picked up the old one mid-request finishes with the old one, and the next request gets the new one.
There is no window in which a half-updated rule can run. A plain `volatile` field or a
`ConcurrentHashMap` is sufficient; nothing here needs a lock.

## Interpreting per run

One interpreter per execution, used once, discarded. It is a small object and allocating one is not
a cost worth optimising.

The temptation to pool them should be resisted. An interpreter holds the variables of a run; a
pooled one holds the variables of somebody else's claim, and the bug that follows is the worst
kind — intermittent, data-dependent, and invisible in testing.

## Three shapes

**A web application.** Seal at startup. Compile rules at startup or on change. Per request: build an
interpreter, register the request-scoped services, supply the arguments, run, use the answer. The
interpreter's lifetime is inside the request, which makes it the natural place for a transaction or
a tenant-specific store.

**A batch job.** Seal once, compile once, then a fresh interpreter per record. Parallelising across
records needs nothing beyond not sharing an interpreter — the language and the program are already
safe, and there is no shared mutable state to contend on. This is where the design pays off most
visibly: throughput scales with cores, and there is no synchronisation to reason about.

**A queue consumer.** The same as a batch job, with the added case of redelivery. A rule that has
run once and is run again on a redelivered message will do its side effects twice, because BUBAS has
no notion of a transaction. Idempotency is the application's problem, and the place to solve it is
in the handlers or around the consumer — not in the rule, which should not know that queues exist.

## What has no answer yet

Two gaps, stated plainly because a book should not leave them to be discovered.

**There is no timeout, and no step budget.** A program can loop forever, and nothing will stop it.
The interpreter walks the program one statement at a time, so a budget or a deadline is a small
addition rather than a redesign — but it is not there. Until it is, a rule from an untrusted source
needs the containment any untrusted workload needs: its own thread, watched from outside, with the
usual unpleasantness about what to do when it will not stop. Chapter 33 is about the wider version
of this.

**There is no memory bound.** An array sized from an expression is sized at runtime, and nothing
checks that the size is sane. `DECLARE lines[n] Item` with a large `n` allocates. For rules your own
team writes this is a non-issue; for anything else it is the same containment problem.

Neither is hard to live with when the rules come from colleagues. Both matter as soon as they do
not, which is precisely the case the last two chapters take up.
