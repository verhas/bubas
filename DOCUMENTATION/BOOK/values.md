# Values

<!-- abstract -->
The four kinds of value a program can hold — whole numbers, decimals, text, true and false — and
why money is always `DECIMAL` and never a floating-point approximation of itself. There is no null:
a value that has not been worked out yet cannot be read at all, a rule with consequences taken up
in chapter 8.
<!-- /abstract -->

---

## What a program can hold

Four kinds of value, and that is the whole list:

| Written as | Called | For |
|---|---|---|
| `42`, `0`, `-10` | `INTEGER` | whole numbers — counts, days, positions |
| `3.14`, `0.5`, `1000.0` | `DECIMAL` | money, rates, anything with a fractional part |
| `"over the limit"` | `STRING` | text |
| `TRUE`, `FALSE` | `BOOLEAN` | yes and no |

There is a fifth kind, and it behaves differently enough to get its own chapter: values from the
domain itself, like the `Report` in the last chapter. A program can hold one and pass it around but
cannot look inside it or write one down. Chapter 5 is about those.

Everything else you might expect is missing. There are no dates, no lists, no records, no maps, no
objects with fields. If your rule needs to know whether a claim was filed within thirty days, that
is a question for an operation, not something you assemble out of primitives. This is a language
for *deciding*, and the things it can hold are the things decisions are made of.

## Whole numbers

`INTEGER` is a 64-bit signed whole number, so it runs to a little over nine quintillion in either
direction. For counting days, line items or people, it will not run out.

Three behaviours are worth knowing before they surprise you.

**Division throws away the remainder.** `7 / 2` is `3`, not `3.5`. `-7 / 2` is `-3` — it truncates
toward zero rather than rounding down. If you want the fractional part, one side has to be a
`DECIMAL`.

**`MOD` gives the remainder**, and takes its sign from the left-hand side: `-7 MOD 2` is `-1`.

**Overflow is an error, not a wraparound.** A calculation that runs past the end of the range stops
the program rather than quietly continuing with a negative number. This is the opposite of what
most languages do, and it is the right choice for a language deciding what to pay: a rule that
silently produces nonsense is worse than one that stops.

Dividing by zero is also an error rather than a special value.

## Money

`DECIMAL` is the type for anything you would put in a currency column, and it is the reason this
chapter exists.

Most programming languages, asked to add 0.10 and 0.20, will tell you the answer is
0.30000000000000004. That is not a bug; it is what happens when you store base-ten fractions in
base two, and every experienced programmer has learned to work around it. BUBAS does not have that
problem to work around, because `DECIMAL` is not a floating-point number. It stores the digits you
wrote.

Here is a claim for ten cents and twenty cents, run through a per-diem rule:

<!--INCLUDE
from: "../../bubas-doc/target/doc-outputs/stage1-values.txt"
prefix: "```"
postfix: "```"
_content_generated_: 441:md5:6131bc7edc3b134acd07676bebe6e95e
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```
ApprovePerDiem(claim = report 6 (Nadia), dailyCap = 20.00, days = 1)
    approved report 6 (Nadia) for 0.30
    => TRUE

ApprovePerDiem(claim = report 7 (Omar), dailyCap = 20.00, days = 1)
    approved report 7 (Omar) for 10.50
    => TRUE

ApprovePerDiem(claim = report 8 (Priya), dailyCap = 30.00, days = 3)
    rejected report 8 (Priya) — averages 33.33333333333333333333333333333333 a day over 3 days, cap is 30.00
    => FALSE
```
<!--/INCLUDE-->

Three things are on display there.

**Addition, subtraction and multiplication are exact.** Ten cents plus twenty cents is thirty
cents, and there is no version of this language in which it is not.

**Scale is preserved.** Omar's bus fare renders as `10.50`, not `10.5`. The trailing zero is
information — it is the difference between a price and a rounded price — and the language keeps it.

**Division is the exception, and it shows.** Priya's hundred euros over three days comes out as
`33.33333333333333333333333333333333`: thirty-four significant digits, which is as far as the
default settings go. Division cannot be exact — a third has no finite decimal expansion — so
somewhere a line has to be drawn, and the application draws it. An application that wants two
decimal places and banker's rounding says so when it builds the language, and every division in
every rule follows suit — in testing exactly as in production.

The practical consequence for a rule writer: **do not divide unless you mean to**. Comparing a
total against a cap is exact and always will be. Working out an average and comparing that is a
calculation whose last digits depend on a setting somewhere else, which is fine as long as you are
not deciding a marginal case on the thirtieth decimal place.

An `INTEGER` used where a `DECIMAL` is wanted is widened automatically. The rule above is declared
with one of each and divides them without ceremony:

<!--INCLUDE
from: "../../bubas-doc/src/test/resources/programs/per-diem.bu"
start:
  pattern: 'PROGRAM ApprovePerDiem'
  include: true
end:
  pattern: 'perDay = total / days'
  include: true
prefix: "```"
postfix: "```"
margin: 0
_content_generated_: 202:md5:fd5c1c55d4bf0f02274cb9a6c233814a
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```
PROGRAM ApprovePerDiem(claim Report, dailyCap DECIMAL, days INTEGER) RETURNS BOOLEAN
    DECLARE total DECIMAL
    DECLARE perDay DECIMAL

    total = TOTAL_OF(claim)
    perDay = total / days
```
<!--/INCLUDE-->

`days` is an `INTEGER`, `total` is a `DECIMAL`, and `perDay` is a `DECIMAL` because that is what
the division produces. The widening only goes one way: a `DECIMAL` where an `INTEGER` is wanted is
an error, because narrowing would have to throw something away and the language will not choose
what.

## Text

`STRING` is text in double quotes, with `\n`, `\t`, `\r`, `\\` and `\"` available as escapes.

Text is joined with `+`, and there is exactly one rule to remember: **the left-hand side must
already be text**. `"Count: " + 42` gives `Count: 42`. Writing `42 + " items"` is an error, because
the left side is a number and BUBAS will not silently decide you meant text. Start with `""` if you
need to lead with a number.

The rule has one consequence that catches people. Since `+` groups from the left, `"n=" + 1 + 2` is
`n=12`, not `n=3`. The first `+` produces text, so the second one joins to it. If you want the sum,
bracket it.

There is no formatting language — no placeholders, no percent signs, no format strings to learn.
Numbers render the way you would write them: whole numbers in plain digits, decimals in plain
notation with their scale kept and never in scientific form, and `TRUE` or `FALSE` exactly as you
would type them.

A domain value cannot be turned into text by the language. BUBAS has no idea what a `Report` looks
like, so `"claim " + claim` is an error. What the domain can do is provide an operation for it —
something that answers a `STRING` — and then the words are the domain's own rather than a rendering
BUBAS invented. If your vocabulary has no such operation and a message needs to name the claimant,
that is an operation to ask for.

## True and false

`BOOLEAN` is `TRUE` or `FALSE`, written in capitals, and there is nothing else in the type. No
truthy numbers, no empty string counting as false, no null pretending to be false. A condition is
either a `BOOLEAN` or it does not compile.

Two booleans can be compared with `=` and `<>` and nothing else — asking whether `TRUE` is greater
than `FALSE` is not a question the language will accept, because it is not a question.

## Comparing things

All six comparisons — `=`, `<>`, `<`, `>`, `<=`, `>=` — work on numbers, and on text, where they
order it character by character. Note that equality is written `=`; there is no `==` in this
language, and `<>` rather than `!=` is inequality.

Numbers of different kinds compare fine; the `INTEGER` is widened to `DECIMAL` first. And `DECIMAL`
comparison is by *value*, not by how it was written, so `2.0 = 2.00` is `TRUE`. That is worth
knowing precisely because scale is otherwise preserved so carefully: the language keeps the trailing
zero when showing you the number, and ignores it when comparing.

Domain values cannot be compared at all — not even for equality. Chapter 5 explains why that
follows from what they are, and what to do when you genuinely need to know whether two claims are
the same one.

## The absence that is not there

There is no null in BUBAS. No `NULL` literal, no test for it, nothing that produces it.

This is not a claim that nothing can ever be missing. The world is full of missing things: a claim
number that matches nothing, an approver who has left. What is absent is the *language-level* value
that stands for all of them at once and behaves like a valid value right up until it does not.

A rule that has to handle absence asks a question about it, through an operation built for the
purpose — `IF CLAIM_WAS_FOUND(claim) THEN`, or whatever that domain calls it. It is an ordinary
operation returning an ordinary `BOOLEAN`, provided by whoever built the vocabulary because this
domain needed it. The absence is named, in domain terms, at the point where
it matters — rather than being a universal shadow value that every operation must defend against.

There is a second half to this, and it is the more useful one. A variable that has been declared but
not yet worked out cannot be read *at all*. Not "reads as null", not "reads as zero" — the program
does not compile. Chapter 8 is about that rule and the family of bugs it eliminates.

## Names and types

One rule about names belongs here rather than later, because it bites when you first write a
declaration.

Every name in a program — variables, operations, and the domain types — shares a single namespace,
and names are unique **case-insensitively**. `Report` is a type in this language, so `report` is
taken, and so are `REPORT` and `rEpOrT`. That is why the last chapter's program called its variable
`claim`.

Once a name is declared, references to it must match it exactly, character for character. So
`userId` and `UserID` cannot both exist, and having declared `userId` you cannot then write
`UserId`. One rule rules out both the lookalike pair and the capitalisation typo.

Naming a variable after its type is the first thing most people reach for, so expect to meet this
rule early. The compiler says plainly what happened.

## What is coming

You now know what a BUBAS program can hold. The next chapter is about the two kinds of operation —
the ones that answer questions and the ones that do things — and after that, the domain values that
this chapter kept deferring.
