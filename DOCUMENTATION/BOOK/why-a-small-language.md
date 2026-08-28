# Why a small language

<!-- abstract -->
The problem before the solution: a rule nobody accountable can read, approved by somebody who can
only check that it looks reasonable. Why this is a question of authority rather than competence,
and why it does not go away when the tooling gets better. Ends with the alternative — do not
constrain a large language, supply a small one — and what that costs.
<!-- /abstract -->

---

## The review that does not happen

A pull request arrives. Two hundred lines of Java implementing the new expense policy: per-category
caps, a receipt threshold, an exception for field engineers that nobody can quite explain, and an
escalation path that depends on the claimant's grade. It compiles. The tests pass.

Somebody now has to approve it.

The person who owns that policy works in finance. She wrote the rules, she argued them through with
the works council, and she is the one who will be asked to explain them when an auditor comes
calling. She cannot read Java.

The person who can read Java is a backend engineer. He has no idea whether field engineers really
are exempt from the receipt threshold, or whether the escalation is supposed to trigger at the
claimant's grade or their manager's. He will check that the code looks reasonable, because that is
the only thing he is equipped to check.

So the review that happens is not the review that matters. One person can read the artefact and
cannot judge it; the other can judge it and cannot read it. The approval goes through, and what has
actually been verified is that the code is not obviously badly written.

This is not a story about a badly run team. Every part of it is normal. The engineer is doing his
job properly. The finance manager is not being lazy — she has been handed a document in a language
she has no reason to know. The gap between them is structural, and it is where this book starts.

## Competence is not the issue

Most complaints about code that implements business rules — and especially about generated code —
are complaints about competence. It gets an edge case wrong. It handles the happy path and falls
over in production. It hallucinates a method that does not exist.

Those complaints expire. Tooling improves, models improve, and any position resting on today's
error rate has a shelf life. People who staked one out a few years ago have mostly had to retreat
from it, quietly.

The durable question is a different one. It is not how well the rule is written. It is **what the
thing it is written in is permitted to say.**

A perfectly competent engineer, writing Java, can still reach the file system. Not because he
intends to, and not because he is careless, but because that sentence is available in the language
he was asked to write. Competence and authority are separate axes, and improving one does nothing
to the other. A flawless implementation of your expense policy, written in a general-purpose
language, still sits inside a program that could open a socket.

That matters less when a trusted colleague writes it and more when the author is a contractor, a
distant team, or a model. But the structural fact is the same in all four cases, and it is the fact
this book is about.

## What "write it in Java" authorises

Consider what you actually grant when you ask for an expense rule in Java.

You grant file system access. Network sockets. Reflection. Thread creation. Process execution.
Every class on the classpath, including the ones that talk to your database, your payment provider
and your secrets manager. You grant the loading of new code at run time.

Nobody intends to grant any of this. It arrives with the language, the way a key to the building
also opens every cupboard in it. The task needed perhaps six operations — look at a claim, total
it, check a receipt, compare against a limit, record a decision, notify somebody — and the language
handed over contains everything Java contains.

That gap, between the authority the task requires and the authority the language confers, is the
whole of the problem. It exists whether or not the author is trustworthy. It exists whether or not
anyone ever acts on it. It is simply very large, and it is invisible in the diff.

## The usual answer is a deny-list

The standard responses all have the same shape.

Review the code. Run static analysis and flag dangerous calls. Forbid certain imports. Run it in a
sandbox with a restricted policy. Tell the author, in the ticket, not to touch the file system.

Every one of these asks you to enumerate what must not happen, across a space of things that can
happen which is effectively unbounded. You have to think of process execution. Then of reflection
reaching process execution. Then of the dependency that shells out on your behalf. Then of the next
one, which you have not thought of yet, which is the entire difficulty.

Security settled this argument a long time ago and reached an answer: allow-lists beat deny-lists,
because the allow-list is finite and you wrote it. Somehow, when the subject is business logic, we
reach for the deny-list again.

## Supply a small language instead

The alternative is to stop constraining a powerful language and instead supply a small one.

Give the author a vocabulary containing exactly the operations the domain has, and nothing else.
Not a restricted Java — a different, much smaller language, whose entire vocabulary is a list your
team wrote in advance, in Java, on purpose.

Here is an expense rule in such a language:

<!--INCLUDE
from: "../../bubas-doc/src/test/resources/programs/approve-expense.bu"
prefix: "```"
postfix: "```"
_content_generated_: 284:md5:f41e6c05922723c1b4d17dab887cfac4
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```
PROGRAM ApproveExpense(claim Report, limit DECIMAL) RETURNS BOOLEAN
    DECLARE total DECIMAL

    total = TOTAL_OF(claim)

    IF total > limit THEN
        REJECT claim, "over the " + limit + " limit"
        RETURN FALSE
    END IF

    APPROVE claim
    RETURN TRUE
END.
```
<!--/INCLUDE-->

`TOTAL_OF`, `APPROVE` and `REJECT` are not part of BUBAS. BUBAS has no such words. They are Java
classes somebody on your team decided to expose, and in a different application the same language
would contain a completely different set. `Report` is a Java object the program can hold and pass
and never look inside — there is no `claim.employee.manager.email` here, only the operations the
domain chose to have.

You read that program. You probably read it without deciding to. More usefully: you can tell
whether it is *right*. Should a claim over the limit be refused outright, or sent to a manager?
Should the claimant be shown the limit in the rejection? You may have opinions about those, and
having opinions about those is exactly what the finance manager was unable to do with two hundred
lines of Java.

## Two things change, and the second matters more

The obvious change is that dangerous programs are no longer forbidden — they are **inexpressible**.
If a program says `DELETE_ALL_CLAIMS`, nothing rejects it on policy grounds. The name means
nothing. The program does not compile, for the same reason a typo does not compile. There is no
deny-list because there is nothing to deny.

The less obvious change is that the review moves to the person who owns the rule. She can read the
program above, and she can be held to it. That is the review that was missing at the start of this
chapter, and no amount of static analysis over generated Java produces it.

There is a third thing, quieter than either. With no data structures, one scope, no null, and a
compiler that refuses to run a program which reads a variable before it has been set, entire
families of subtle wrongness have nowhere to live. Not caught — absent. Chapter 8 is about what
that feels like from the inside.

## What it costs

Any argument that only lists advantages should be distrusted, so here are the bills.

**You have to design the vocabulary.** Somebody sits down and decides that this domain has
`TOTAL_OF` and `HAS_RECEIPT` and not forty other things. That is real work, done before the first
rule is written, by someone who understands the domain. If nobody on your team can write that list,
this approach will not help you — it will only show you that the list does not exist. Worth finding
out, but not a pleasant morning. Part 3 is largely about doing this well.

**Complex algorithms stay in Java.** Business rules are algorithms too, and they belong in the
small language; that is the point. But route optimisation, a scoring model, anything with real
computational substance belongs behind an operation the small language calls. The usual signal is
that you want to build up a data structure, or want a helper you can call from three places. Both
mean you have wandered out of business logic and should walk back. Chapter 9 gives this its own
treatment.

**The boundary bounds naming, not doing.** This is the limit people miss, and overstating it is how
the whole idea gets dismissed. An operation you expose can do anything Java can do.
`RUN_SHELL_COMMAND` is a perfectly registrable operation. The vocabulary is only as narrow as the
operations you chose, and choosing them badly gets you exactly the exposure you were avoiding.

**There are no resource limits yet.** A program can still loop forever. This is a gap rather than a
decision: the interpreter walks the program one statement at a time, so a step budget or a deadline
is a small addition rather than a redesign, and it will go in when somebody needs it. Until then,
untrusted input needs the containment any untrusted workload needs. Chapter 33 is honest about
this at length.

What you get is narrower than "safe" and more useful than it sounds: the set of things a program
can name is finite, written down, and reviewable by a human before anything is written at all.

## When not to do this

If the thing is genuinely computational, write Java.

If it is a one-off that will be deleted next week, use whatever is nearest and do not build a
vocabulary for something with a life expectancy of days.

If your rules change so fast that the vocabulary would be obsolete before it settled, the overhead
will not pay for itself — though in practice, domains whose *operations* churn that fast are rarer
than domains whose *thresholds* churn, and thresholds are cheap.

And if your business logic is already read, understood and approved by the people accountable for
it being right, you may not have the problem this solves. Plenty of teams do not.

## How to read this book

The book is in three parts.

**Part 1** is the language: how to read a BUBAS program and how to write one. It contains no Java
at all, and it is written for whoever owns the rules as much as for the engineer who will embed the
language. Both need it, for the same reason — you cannot design a language you have not learned to
use.

**Part 2** is testing. BUBAS programs are tested in BUBAS, so that the person who reviews the rule
can also read the check on it. Also no Java.

**Part 3** is embedding: defining a vocabulary, wiring it into an application, and running it in
production. This is where Java lives, and Parts 1 and 2 point forward to it constantly, because a
rule writer who wants an operation that does not exist yet needs somebody to go and build it.

One worked application runs through all three parts: approving employee expense claims. It begins
as a total and a limit, and grows into something with receipts, per-category caps, escalation to a
manager, and an anomaly score from a model. Nothing is ever rewritten to make a later chapter work.
Each chapter adds.

Expense approval is used because you can audit it. Shown a rule that pays a €340 dinner for one
person with nothing attached, you know something is wrong without being told. That is the
experience this book is trying to give you: not the claim that experts can review these programs,
but a page on which you do it.

The next chapter is a complete program, start to finish.
