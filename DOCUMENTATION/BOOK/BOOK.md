# BUBAS

**Orchestration for people who own the rules**

**Status** Skeleton. Chapter descriptions agreed; content to be written chapter by chapter.

---

**Contents**

<!--TOC min-level: 2
max-level: 2
_content_generated_: 167:md5:3493b44a9437c2e8fb02a6fa6dc3da4d
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
- [About this book](#about-this-book)
- [Part 1 — The Language](#part-1-the-language)
- [Part 2 — Testing](#part-2-testing)
- [Part 3 — Embedding](#part-3-embedding)
<!--/TOC-->

---

## About this book

Business rules are written in general-purpose languages by people who do not own them, and reviewed
by people who cannot read them. BUBAS is an answer to that: a language with no vocabulary of its
own, whose every word is an operation your team decided to expose, small enough that the person
accountable for a rule can read the program that implements it.

This book covers the language, how programs written in it are tested, and how the whole thing is
embedded in a Java application.

### Who it is for

Two readers, and they part company at the end of Part 2.

The **subject-matter expert** writes and reviews rules. Parts 1 and 2 are written for them
throughout and contain no Java at all: not one line, not one class name, nothing to skip past. A
finance manager can be handed the first two parts whole.

The **programmer** embedding BUBAS reads the same first two parts, for the same reason — you cannot
design a language you have not learned to use — and then continues into Part 3, which is entirely
about building and running one.

That Java appears only in Part 3 is the organising rule of the book, not an accident of ordering.
Defining a vocabulary is embedding, and embedding is Part 3.

### The example

One application runs through the whole book: approving employee expense claims. It starts as a
total and a limit, and grows, chapter by chapter, into something with receipts, per-category caps,
manager escalation, and an anomaly score from a model. Nothing is ever rewritten to make a later
chapter work — each chapter adds.

Expense approval is used because you can audit it. Shown a rule that pays a €340 dinner for one
person with no receipt attached, you know something is wrong without being told, and that is the
experience the book is trying to give you: not a claim that experts can review these programs, but
a page where you do it.

### Conventions

Every program, fragment and transcript in this book is produced by a build. The programs are
compiled, the outputs are what running them actually printed, and the compiler messages are what
the compiler actually said. Nothing here was typed from memory, and a change to the code that
outdated a page would fail the build that produced it.

Each chapter opens with the vocabulary the language has at that point, generated from the language
itself, so that a reader arriving in the middle is never guessing what is available.

---

## Part 1 — The Language

What BUBAS is, and how to read and write programs in it. No Java.

### 1. [Why a small language](why-a-small-language.md)

<!--INCLUDE
from: "why-a-small-language.md"
start: '<!-- abstract'
end: '<!-- /abstract'
_content_generated_: 359:md5:b53cf5c04ad59edf29f06c4025352844
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
The problem before the solution: a rule nobody accountable can read, approved by somebody who can
only check that it looks reasonable. Why this is a question of authority rather than competence,
and why it does not go away when the tooling gets better. Ends with the alternative — do not
constrain a large language, supply a small one — and what that costs.
<!--/INCLUDE-->

### 2. [A first rule](a-first-rule.md)

<!--INCLUDE
from: "a-first-rule.md"
start: '<!-- abstract'
end: '<!-- /abstract'
_content_generated_: 293:md5:69c9ec6d0b895222d66c2094f86091ba
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
A complete program, start to finish: a claim comes in, a total is worked out, a decision is made
and recorded. Program structure, parameters, `DECLARE`, `RETURN`, and what running it looks like.
By the end of this chapter you can read a BUBAS program, which is most of what this book is for.
<!--/INCLUDE-->

### 3. [Values](values.md)

<!--INCLUDE
from: "values.md"
start: '<!-- abstract'
end: '<!-- /abstract'
_content_generated_: 310:md5:739fe75c66b68261a3af85b71b44ad58
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
The four kinds of value a program can hold — whole numbers, decimals, text, true and false — and
why money is always `DECIMAL` and never a floating-point approximation of itself. There is no
null: a value that has not been worked out yet cannot be read at all, a rule with consequences
taken up in chapter 8.
<!--/INCLUDE-->

### 4. [Asking and telling](asking-and-telling.md)

<!--INCLUDE
from: "asking-and-telling.md"
start: '<!-- abstract'
end: '<!-- /abstract'
_content_generated_: 274:md5:199885cfa4ec9962e944e8fe494c01ac
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
Two kinds of operation, distinguished on sight. A function is asked a question inside an expression
and answers with a value; a command is told to do something and stands alone on its line. Why the
distinction is worth making, and how to tell which one you are looking at.
<!--/INCLUDE-->

### 5. [Things the program cannot open](things-the-program-cannot-open.md)

<!--INCLUDE
from: "things-the-program-cannot-open.md"
start: '<!-- abstract'
end: '<!-- /abstract'
_content_generated_: 340:md5:ed749b42e87f9c19706987791b1da88f
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
Some values a program holds are sealed: it can be given one, pass it on, and ask questions about
it, but never reach inside. There is no `claim.employee.manager.email`, and this chapter argues
that the missing dot is a feature. What it means for you as a rule writer, and what to do when the
question you want to ask has no operation yet.
<!--/INCLUDE-->

### 6. [Deciding](deciding.md)

<!--INCLUDE
from: "deciding.md"
start: '<!-- abstract'
end: '<!-- /abstract'
_content_generated_: 287:md5:76a1a1594aced07b6d3015a206c8b3d3
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
`IF`, `ELSEIF`, `ELSE`, comparison, and `AND` / `OR` / `NOT` written as the words they are. How the
order of tests is itself part of the policy — a claim caught by the meals cap before the total is
ever considered is a decision somebody made, and it should be one they made on purpose.
<!--/INCLUDE-->

### 7. [Repeating](repeating.md)

<!--INCLUDE
from: "repeating.md"
start: '<!-- abstract'
end: '<!-- /abstract'
_content_generated_: 200:md5:389c25114857869cf9dd5ea4ae8f431b
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
Walking the lines of a claim: `FOR`, `DO WHILE`, `DO UNTIL`, and leaving early with `EXIT`.
Counting starts at one, because the first line on an expense claim is line 1 and nothing here is an
array.
<!--/INCLUDE-->

### 8. [What will not compile](what-will-not-compile.md)

<!--INCLUDE
from: "what-will-not-compile.md"
start: '<!-- abstract'
end: '<!-- /abstract'
_content_generated_: 327:md5:70e41aca735cc73b611b214ef835dc61
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
The refusals: a value read before it has been worked out, a constant assigned twice, a variable
declared and never used. This chapter argues that these are not the compiler being fussy — with no
null, one scope and no data structures, whole families of mistake have nowhere left to live. They
are not caught. They are absent.
<!--/INCLUDE-->

### 9. [Arrays, and wanting one](arrays-and-wanting-one.md)

<!--INCLUDE
from: "arrays-and-wanting-one.md"
start: '<!-- abstract'
end: '<!-- /abstract'
_content_generated_: 292:md5:cb0b307ba0948bed75abbacb3c72d94e
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
The one aggregate BUBAS has, what it can and cannot do, and a warning. Wanting a data structure is
usually a signal that you have stopped writing a business rule and started writing an algorithm —
which is a reasonable thing to want, and belongs behind an operation rather than in the rule.
<!--/INCLUDE-->

### 10. [Operations that consult a model](operations-that-consult-a-model.md)

<!--INCLUDE
from: "operations-that-consult-a-model.md"
start: '<!-- abstract'
end: '<!-- /abstract'
_content_generated_: 347:md5:2d18cf810fd5a99462b2b83ae5797382
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
An operation that returns how unusual a line of spending looks, on a scale of one to ten, and is
backed by a model rather than a calculation. The score is advice; the threshold you compare it
against is in your program, where you own it. What it costs to have an operation that will not
give the same answer twice — which is where Part 2 begins.
<!--/INCLUDE-->

### 11. [Knowing what you can say](knowing-what-you-can-say.md)

<!--INCLUDE
from: "knowing-what-you-can-say.md"
start: '<!-- abstract'
end: '<!-- /abstract'
_content_generated_: 385:md5:e12d922b58dddeed86a65226a3dcfc41
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
Every BUBAS language can describe itself: a document listing every operation, what it answers, and
what it is for, written for you rather than for a programmer. How to read it, how to tell whether a
rule you have been asked to write is expressible, and how to ask for an operation that does not
exist yet. That last one is a normal request, and Part 3 is where it gets answered.

---
<!--/INCLUDE-->

## Part 2 — Testing

How to check that a rule does what its owner intended. Still no Java.

### 12. [Why the test must be readable too](why-the-test-must-be-readable-too.md)

<!--INCLUDE
from: "why-the-test-must-be-readable-too.md"
start: '<!-- abstract'
end: '<!-- /abstract'
_content_generated_: 259:md5:fbe90edd224865545a2811946cbc748d
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
A rule reviewed by the person accountable for it needs a test that the same person can check.
A test written in a language they cannot read moves the review straight back to where chapter 1
found it. BUNIT tests are written in BUBAS for exactly this reason.
<!--/INCLUDE-->

### 13. [A first test](a-first-test.md)

<!--INCLUDE
from: "a-first-test.md"
start: '<!-- abstract'
end: '<!-- /abstract'
_content_generated_: 205:md5:c2d51d3c89c7de82cbaff6d6f2f5f033
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
A complete test of the rule from chapter 2: the claim it is given, what it should decide, and what
the runner prints when it agrees and when it does not. Enough to write tests for rules you already
have.
<!--/INCLUDE-->

### 14. [Standing in for the world](standing-in-for-the-world.md)

<!--INCLUDE
from: "standing-in-for-the-world.md"
start: '<!-- abstract'
end: '<!-- /abstract'
_content_generated_: 273:md5:8dde312c4920d2669c9328a2ea7d9f59
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
Your rule calls operations that load claims, send messages and charge budgets, and a test cannot
have those really happen. Mocking as an idea: the rule under test stays real, everything it reaches
for is pretended. What that buys, and the one thing it can never tell you.
<!--/INCLUDE-->

### 15. [Tokens](tokens.md)

<!--INCLUDE
from: "tokens.md"
start: '<!-- abstract'
end: '<!-- /abstract'
_content_generated_: 229:md5:dd3e8f0b4685e9da49366ded64bc8e3a
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
Some values cannot be constructed in a test, because they are sealed — you can hold a claim but not
build one. Tokens are how a test names a value it cannot make, and how the same value is recognised
when the rule passes it on.
<!--/INCLUDE-->

### 16. [Matching arguments](matching-arguments.md)

<!--INCLUDE
from: "matching-arguments.md"
start: '<!-- abstract'
end: '<!-- /abstract'
_content_generated_: 210:md5:47a6a0e057faf2edfc18012aa2f225dd
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
Saying which calls a mock should answer: exact values, ranges, anything at all, anything but. How
much to pin down, and why pinning down everything produces a test that fails whenever anyone
touches anything.
<!--/INCLUDE-->

### 17. [Leaving some of it real](leaving-some-of-it-real.md)

<!--INCLUDE
from: "leaving-some-of-it-real.md"
start: '<!-- abstract'
end: '<!-- /abstract'
_content_generated_: 169:md5:69cd86caae4231f9ba184e7498a343e6
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
Not everything needs standing in for. Partial mocking, when leaving an operation real makes a test
more honest, and when it quietly hides the thing you meant to check.
<!--/INCLUDE-->

### 18. [Mocks that cannot be right](mocks-that-cannot-be-right.md)

<!--INCLUDE
from: "mocks-that-cannot-be-right.md"
start: '<!-- abstract'
end: '<!-- /abstract'
_content_generated_: 285:md5:3eef514dbd30d4d68f436aec96636b1f
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
A mock that sets no value the rule then reads, or answers a call the rule never makes, is not a
test — it is a test-shaped object that passes. What the consistency checker refuses, and why it is
deliberately narrow: a check that fires too widely destroys the thing it was protecting.
<!--/INCLUDE-->

### 19. [Testing what cannot be tested](testing-what-cannot-be-tested.md)

<!--INCLUDE
from: "testing-what-cannot-be-tested.md"
start: '<!-- abstract'
end: '<!-- /abstract'
_content_generated_: 326:md5:8a28c0295c350e3eef4bca7c204da595
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
The model-backed operation from chapter 10 will not give the same answer twice, so there is no
version of this test that does not stand in for it. Mocking stops being good practice here and
becomes the only way the rule can be checked at all — and the rule around it, the threshold you
chose, is what you are really testing.
<!--/INCLUDE-->

### 20. [A suite that stays honest](a-suite-that-stays-honest.md)

<!--INCLUDE
from: "a-suite-that-stays-honest.md"
start: '<!-- abstract'
end: '<!-- /abstract'
_content_generated_: 246:md5:3e540af2f9e76bcd803743cdb6367ab4
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
Organising tests so they keep their value: what to cover, what not to bother with, and the coverage
claim a finite vocabulary makes possible that a general-purpose language cannot — you can enumerate
every operation a rule is able to call.

---
<!--/INCLUDE-->

## Part 3 — Embedding

Building a language and running it in production. This is where Java lives.

### 21. [Three objects](three-objects.md)

<!--INCLUDE
from: "three-objects.md"
start: '<!-- abstract'
end: '<!-- /abstract'
_content_generated_: 263:md5:3f9ad7250497d9af37642ae424413573
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
The whole architecture in one chapter: a language that is defined once and sealed, a program that
is compiled once and reused, and an interpreter that is cheap, single-use and single-threaded.
What each costs, what each is safe to share, and why sealing exists.
<!--/INCLUDE-->

### 22. [Defining types](defining-types.md)

<!--INCLUDE
from: "defining-types.md"
start: '<!-- abstract'
end: '<!-- /abstract'
_content_generated_: 245:md5:db3ca604b929ee47ff31896a44feb690
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
Opaque types from the other side. How a Java class becomes something a program can hold, why
opacity is total rather than partial, and what you give up by exposing an operation instead of a
field — along with what that buys back in chapter 27.
<!--/INCLUDE-->

### 23. [Defining functions](defining-functions.md)

<!--INCLUDE
from: "defining-functions.md"
start: '<!-- abstract'
end: '<!-- /abstract'
_content_generated_: 253:md5:a0b64bc2a98c237886c9d4423f7e79d8
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
Operations that answer questions: handler classes, the context they are given, variable arity, and
wildcard parameters. Which work belongs behind a function, and the signal that you are about to put
business logic somewhere your experts cannot see it.
<!--/INCLUDE-->

### 24. [Defining commands](defining-commands.md)

<!--INCLUDE
from: "defining-commands.md"
start: '<!-- abstract'
end: '<!-- /abstract'
_content_generated_: 271:md5:e04368e5150d5e923d4ca3658dc85e78
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
The pattern language: how `REJECT claim, "over the limit"` becomes a statement, with placeholders,
kinds, type constraints, and conditions on what a variable must be before and after. How the
analysis at sealing time proves no two commands can ever match the same line.
<!--/INCLUDE-->

### 25. [Exposing a model](exposing-a-model.md)

<!--INCLUDE
from: "exposing-a-model.md"
start: '<!-- abstract'
end: '<!-- /abstract'
_content_generated_: 362:md5:a6287209cd0d43d11beaf54ee8060ff2
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
Putting an LLM behind an operation, and the design rule that decides whether it was worth doing:
return a score, never a verdict. Advisory outputs leave the decision with the expert; verdict
outputs move it to the model and reduce your language to glue. Also the honest part — this is the
one operation whose behaviour can change without any checksum changing.
<!--/INCLUDE-->

### 26. [Describing and exporting](describing-and-exporting.md)

<!--INCLUDE
from: "describing-and-exporting.md"
start: '<!-- abstract'
end: '<!-- /abstract'
_content_generated_: 259:md5:2d24e414f59cb9e497c7410b10d12808
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
Writing the vocabulary document that chapter 11 taught people to read: descriptions, where they
live so that domain classes stay clean, the export itself, and a checksum that tells you when a
reviewed vocabulary has changed shape since anybody looked at it.
<!--/INCLUDE-->

### 27. [Extending BUNIT](extending-bunit.md)

<!--INCLUDE
from: "extending-bunit.md"
start: '<!-- abstract'
end: '<!-- /abstract'
_content_generated_: 258:md5:2b0cad051da224a522d1af465c37332d
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
Mock commands of your own, for operations that write values rather than return them. How
interception works, what the consistency checker needs to be told about a command it has never
seen, and why that is expressed as annotations rather than an interface.
<!--/INCLUDE-->

### 28. [Wiring](wiring.md)

<!--INCLUDE
from: "wiring.md"
start: '<!-- abstract'
end: '<!-- /abstract'
_content_generated_: 186:md5:52303e4db0e3e3da23217bc9f6cb8151
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
Services, logging, configuration, and the context handlers are given. Keeping handlers thin, and
where the application's real work should sit so that the vocabulary stays a vocabulary.
<!--/INCLUDE-->

### 29. [Concurrency and lifecycle](concurrency-and-lifecycle.md)

<!--INCLUDE
from: "concurrency-and-lifecycle.md"
start: '<!-- abstract'
end: '<!-- /abstract'
_content_generated_: 196:md5:4b79e56510aef96a987ec763b5046e3c
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
Seal at startup, compile on change, interpret per request. What is shared safely between threads,
what must never be, and how this maps onto a web application, a batch job, and a queue consumer.
<!--/INCLUDE-->

### 30. [Failing well](failing-well.md)

<!--INCLUDE
from: "failing-well.md"
start: '<!-- abstract'
end: '<!-- /abstract'
_content_generated_: 292:md5:f08b4e2661649254613cbf943a388666
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
Two kinds of failure with two audiences. A compile error is shown to the person writing the rule
and should name their line; a runtime error is an operations problem and should never be shown to
them at all. What to log, what to surface, and what to do with a program that will not compile.
<!--/INCLUDE-->

### 31. [Where programs come from](where-programs-come-from.md)

<!--INCLUDE
from: "where-programs-come-from.md"
start: '<!-- abstract'
end: '<!-- /abstract'
_content_generated_: 227:md5:c4420e662593723d0c3a187e5bbc806e
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
Rules have to be written, stored, versioned, reviewed and deployed by people who are not using your
source control. Authoring, storage, review workflow built on the export, and rolling back a rule
that turned out to be wrong.
<!--/INCLUDE-->

### 32. [Programs written by a model](programs-written-by-a-model.md)

<!--INCLUDE
from: "programs-written-by-a-model.md"
start: '<!-- abstract'
end: '<!-- /abstract'
_content_generated_: 241:md5:26fa0537beaebc8ac60dba82de760988
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
The generation case, end to end. What a finite vocabulary actually bounds, what it does not — an
operation you expose can do anything Java can do — and where the remaining risk sits. The
allow-list argument in full, with its limits stated.
<!--/INCLUDE-->

### 33. [Running someone else's rules](running-someone-elses-rules.md)

<!--INCLUDE
from: "running-someone-elses-rules.md"
start: '<!-- abstract'
end: '<!-- /abstract'
_content_generated_: 237:md5:ebb95f15c858d07da013b27a6ea6ea11
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
Everything left: resource limits and the fact that BUBAS does not yet have them, auditing the
decisions a rule made and why, observability, and what containment untrusted input still needs.
Ends with the complete application assembled.
<!--/INCLUDE-->

