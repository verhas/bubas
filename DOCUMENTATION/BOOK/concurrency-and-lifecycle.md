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

## What has an answer, and what has not

**A run can be bounded, and by default is not.** `maxSteps` on the interpreter counts statements
executed and loop passes taken, so a rule that loops forever stops at a number you chose rather than
never. `maxArrayLength` caps what a single `DECLARE lines[n] Item` may bring into existence, which
is the allocation an expression-sized array otherwise makes without anybody agreeing to it. Chapter
28 shows both. They are unlimited unless you set them, so a program that has never been bounded is
still a program that can loop forever — the default is the gap, not the mechanism.

**There is still no deadline.** The budget counts what the program does, and an operation you
registered that blocks for an hour is one step. A rule that calls a slow service is slow, and no
number stops that. If you need a wall clock, it is your thread and your timeout, outside all of
this.

**And the memory bound is per array, not per run.** Ten thousand small arrays, each inside the cap,
are ten thousand arrays. The limit stops the single absurd allocation, which is the mistake that
actually happens; it is not a heap quota.

The shape of it: a rule from a colleague who wrote a slow loop is now a rule that stops. A rule
written to exhaust you is a different problem, and the last two chapters take it up.
