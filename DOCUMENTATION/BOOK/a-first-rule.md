# A first rule

<!-- abstract -->
A complete program, start to finish: a claim comes in, a total is worked out, a decision is made
and recorded. Program structure, parameters, `DECLARE`, `RETURN`, and what running it looks like.
By the end of this chapter you can read a BUBAS program, which is most of what this book is for.
<!-- /abstract -->

---

## The whole thing

Here is a complete BUBAS program. Not an excerpt, not a simplified version — this is the entire
file, and it compiles and runs exactly as printed:

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

Thirteen lines. It decides whether an expense claim can be paid without anyone looking at it.

The rest of this chapter takes it apart. If you read the program and understood it, that is the
correct reaction and you should not be suspicious of it — the language is small on purpose, and a
program that needed explaining would be a failure of the design rather than a sign of depth.

## The first line

<!--INCLUDE
from: "../../bubas-doc/src/test/resources/programs/approve-expense.bu"
start:
  pattern: 'PROGRAM ApproveExpense'
  include: true
end:
  pattern: 'DECLARE total'
  include: false
prefix: "```"
postfix: "```"
margin: 0
_content_generated_: 77:md5:9936563eff7abe15c4d764b71ed27af5
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```
PROGRAM ApproveExpense(claim Report, limit DECIMAL) RETURNS BOOLEAN
```
<!--/INCLUDE-->

Every program says what it is called, what it needs, and what it answers.

`ApproveExpense` is the name. It matters when the surrounding application goes looking for a rule
to run; within the file, nothing refers to it.

`claim Report` and `limit DECIMAL` are **parameters** — the things the program is given. Each is a
name followed by its type, in that order, because you are naming a thing and then saying what kind
of thing it is. The claim to decide about, and the limit to decide against.

`RETURNS BOOLEAN` says the program answers true or false. A program need not answer anything; if it
only records decisions and notifies people, leave `RETURNS` off entirely. This one answers, because
whatever called it wants to know whether to pay.

Parameters are the whole reason a program is worth compiling once and running many times. The same
compiled `ApproveExpense` decides every claim that arrives all day, and it decides them differently
because it is given different things:

<!--INCLUDE
from: "../../bubas-doc/target/doc-outputs/stage1-limit-effect.txt"
prefix: "```"
postfix: "```"
_content_generated_: 244:md5:0a46a067eaf273d1d1eab88a19ea5dd7
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```
ApproveExpense(claim = report 1 (Alice), limit = 200.00)
    approved report 1 (Alice) for 128.40
    => TRUE

ApproveExpense(claim = report 1 (Alice), limit = 100.00)
    rejected report 1 (Alice) — over the 100.00 limit
    => FALSE
```
<!--/INCLUDE-->

One claim, two limits, two answers, and nothing recompiled in between.

## Saying what you will need

<!--INCLUDE
from: "../../bubas-doc/src/test/resources/programs/approve-expense.bu"
start:
  pattern: 'DECLARE total'
  include: true
end:
  pattern: 'total = TOTAL_OF'
  include: false
prefix: "```"
postfix: "```"
margin: 0
_content_generated_: 32:md5:0daccc3432868d92d862bd16dd6b38c6
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```
DECLARE total DECIMAL

```
<!--/INCLUDE-->

Every variable is announced before it is used, with its type. There is no way to invent a variable
by assigning to it, and no way to leave the type to be worked out later.

That is more ceremony than a scripting language would ask for, and it is deliberate. A rule that
mentions `totl` on line nine is a rule with a bug that nobody will find by reading, and there is no
good reason for a language in this position to allow it. Declare it, or you cannot use it.

`DECIMAL` is the type for money. There is a separate type for whole numbers, and later chapters use
both. What matters here is that `DECIMAL` means what an accountant means: 0.10 plus 0.20 is 0.30,
exactly, and never 0.30000000000000004. Chapter 3 goes into why that is worth a type of its own.

## Asking a question

<!--INCLUDE
from: "../../bubas-doc/src/test/resources/programs/approve-expense.bu"
start:
  pattern: 'total = TOTAL_OF'
  include: true
end:
  pattern: 'IF total'
  include: false
prefix: "```"
postfix: "```"
margin: 0
_content_generated_: 34:md5:1b340b228e393245380b25f6ba59ff1c
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```
total = TOTAL_OF(claim)

```
<!--/INCLUDE-->

`claim` needed no working out — it arrived as a parameter, because the application already had it
in hand when it decided to run this rule. `total` is different. It has to be worked out, and
working it out means asking something that knows how.

`TOTAL_OF` is an **operation**. It is not part of BUBAS: someone on the team that set up this
language decided that "what does this claim come to" was a question worth being able to ask, and
made it askable. In a different application it would not exist, and something else would.

Operations that answer a question are written with brackets and used inside an expression, the way
you would expect. Chapter 4 deals with the other kind, which does not answer and is written
differently.

`Report` is a type this language has and Java does not care about: the claim itself. A program can
hold one, pass it to an operation, and keep it in a variable. What it cannot do is look inside.
There is no way to write `claim.employee` or `claim.lines[0].amount`, because there is no syntax in
the language for reaching into anything at all. Chapter 5 is about what that is like to live with,
and why it is the single most important decision in the design.

## Deciding

<!--INCLUDE
from: "../../bubas-doc/src/test/resources/programs/approve-expense.bu"
start:
  pattern: 'IF total > limit'
  include: true
end:
  pattern: 'END IF'
  include: true
prefix: "```"
postfix: "```"
margin: 0
_content_generated_: 104:md5:5c533d8fdbca110852001d9f1c573bcf
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```
IF total > limit THEN
    REJECT claim, "over the " + limit + " limit"
    RETURN FALSE
END IF
```
<!--/INCLUDE-->

A test, some statements, and `END IF`. The block is closed explicitly rather than by indentation,
so a rule cannot change meaning because someone's editor reformatted it.

`REJECT` is the other kind of operation. It does not answer a question — it does something, and
there is nothing to assign. So it is written as its own statement, with no brackets: the name, then
whatever it needs, separated by commas. Compare `TOTAL_OF(claim)`, which you use for its answer,
with `REJECT claim, "..."`, which you use for its effect. You can tell them apart on sight, which
is the point.

The reason string is built with `+`. There is no formatting language, no placeholders, no percent
signs; a number joined to text becomes text. `"over the " + limit + " limit"` produces `over the
200.00 limit`, and that is as complicated as string building gets.

`RETURN FALSE` answers immediately and stops. Nothing after it runs.

## Answering

<!--INCLUDE
from: "../../bubas-doc/src/test/resources/programs/approve-expense.bu"
start:
  pattern: 'APPROVE claim'
  include: true
end:
  pattern: 'END\.'
  include: false
prefix: "```"
postfix: "```"
margin: 0
_content_generated_: 35:md5:d73ddd99f54b22113242003dbfc9e358
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```
APPROVE claim
RETURN TRUE
```
<!--/INCLUDE-->

If the claim survived the test, it is approved and the program says so.

`APPROVE` takes one thing and needs no comma. `RETURN TRUE` gives the answer that `RETURNS BOOLEAN`
promised on line one. A program that says it returns a `BOOLEAN` must actually return one on every
path out — the compiler checks, and a rule that could fall off the end without answering does not
compile.

## The last line

`END.` closes the program, and the full stop is part of it. It is the one piece of punctuation in the language that exists purely
so that a truncated file cannot look like a complete one.

## Running it

Two claims, both against a limit of 200.00:

<!--INCLUDE
from: "../../bubas-doc/target/doc-outputs/stage1-decisions.txt"
prefix: "```"
postfix: "```"
_content_generated_: 240:md5:9e2dc796adea37b002456d7099825fd8
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```
ApproveExpense(claim = report 1 (Alice), limit = 200.00)
    approved report 1 (Alice) for 128.40
    => TRUE

ApproveExpense(claim = report 2 (Bob), limit = 200.00)
    rejected report 2 (Bob) — over the 200.00 limit
    => FALSE
```
<!--/INCLUDE-->

Alice's taxi, lunch and hotel come to 128.40 and are approved. Bob's conference fee and dinner come
to 1230.00, and are not.

The indented lines are what `APPROVE` and `REJECT` recorded. What that means in practice — a row in
a table, a message on a queue, an email to the claimant — is entirely up to the application, and
the rule neither knows nor can find out. It says what should happen. Something else makes it
happen.

## What it cannot say

It is worth noticing what is absent, because it is absent by construction rather than by
convention.

There is no way to open a file, reach the network, start a thread, or call anything that was not
put in front of the program deliberately. Not because a checker forbids it — because there are no
words for it. If this program said `SEND_TO_AUDITORS claim`, it would not compile, for exactly the
same reason that `SNED_TO_AUDITORS` would not: the name means nothing.

That is a smaller guarantee than it sounds and a more useful one than it sounds, and chapter 1
argued it at length. Here it is enough to notice that the guarantee is visible in the program: you
can see everything this rule is able to do, because it is all on the page.

## What you can now read

You can read a BUBAS program. That is not a small claim to have earned in thirteen lines, and it is
most of what this book is for.

What you cannot yet do is write one from nothing, or predict what the compiler will refuse. The
next chapters fill that in — values and types, the two kinds of operation, opaque types, then
decisions and loops — and by chapter 8 you will know what will not compile and why the list is
shorter than you expect.
