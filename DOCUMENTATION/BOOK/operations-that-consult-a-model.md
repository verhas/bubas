# Operations that consult a model

<!-- abstract -->
An operation that returns how unusual a line of spending looks, on a scale of one to ten, and is
backed by a model rather than a calculation. The score is advice; the threshold you compare it
against is in your program, where you own it. What it costs to have an operation that will not give
the same answer twice — which is where Part 2 begins.
<!-- /abstract -->

---

## An operation with an opinion

Every operation so far has calculated something. `TOTAL_OF` adds up lines. `HAS_RECEIPT` looks to
see whether a file is attached. Ask either one twice and you get the same answer, because there is
a right answer and it found it.

This one is different:

<!--INCLUDE
from: "../../bubas-doc/target/doc-outputs/vocabulary-added-5.md"
_content_generated_: 198:md5:20db14525bb88c9f6c80d11692a9673c
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
### ANOMALY_SCORE_OF(line Item) -> INTEGER

How unusual a line of spending looks, from 1 (ordinary) to 10 (very unusual).
It is an opinion, not a decision: the rule decides what score is too high.
<!--/INCLUDE-->

There is no right answer to how unusual a dinner looks. Behind that operation is a model — the kind
of thing that reads a line of spending and produces a judgement, the way an experienced person in
accounts payable would. It might notice that the amount is large for its category, that no receipt
is attached, that the merchant is one nobody has claimed against before, that the date is a
Saturday. It does not explain itself, and it will not necessarily say the same thing tomorrow.

Nothing about how a BUBAS program uses it is different. It is a function; you call it; it answers
an `INTEGER`. Everything strange about it is behind the boundary.

## What the rule does with an opinion

<!--INCLUDE
from: "../../bubas-doc/src/test/resources/programs/screened-expense.bu"
prefix: "```"
postfix: "```"
_content_generated_: 677:md5:fae5ff64448f55e48955332328c353ef
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```
PROGRAM ScreenExpense(claim Report, limit DECIMAL, flagAt INTEGER) RETURNS BOOLEAN
    DECLARE i INTEGER
    DECLARE line Item
    DECLARE worst INTEGER

    worst = 0

    FOR i = 1 TO ITEM_COUNT(claim)
        line = ITEM_AT(claim, i)

        IF ANOMALY_SCORE_OF(line) > worst THEN
            worst = ANOMALY_SCORE_OF(line)
        END IF
    END FOR

    IF worst >= flagAt THEN
        ESCALATE claim, "a line scored " + worst + ", and this rule flags at " + flagAt
        RETURN FALSE
    END IF

    IF TOTAL_OF(claim) > limit THEN
        ESCALATE claim, "over the " + limit + " limit"
        RETURN FALSE
    END IF

    APPROVE claim
    RETURN TRUE
END.
```
<!--/INCLUDE-->

The rule walks the lines, keeps the worst score it saw, and compares it against `flagAt`.

That comparison is the whole point of this chapter, so it is worth being blunt about it. **The
model does not decide anything.** It offers a number. The rule decides what number is too high, and
the rule is on the page, in the language of the business, where the person accountable for expense
policy can read it, argue with it, and change it.

Here is the same claim, twice, with only that threshold moved:

<!--INCLUDE
from: "../../bubas-doc/target/doc-outputs/stage5-screening.txt"
prefix: "```"
postfix: "```"
_content_generated_: 420:md5:c5afbd5c6de44284b5205ee044e768e9
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```
ScreenExpense(claim = report 1 (Alice), limit = 2000.00, flagAt = 8)
    approved report 1 (Alice) for 128.40
    => TRUE

ScreenExpense(claim = report 9 (Quentin), limit = 2000.00, flagAt = 8)
    escalated report 9 (Quentin) — a line scored 8, and this rule flags at 8
    => FALSE

ScreenExpense(claim = report 9 (Quentin), limit = 2000.00, flagAt = 9)
    approved report 9 (Quentin) for 411.00
    => TRUE
```
<!--/INCLUDE-->

Quentin's dinner scores 8 both times. At `flagAt = 8` the claim goes for review; at `flagAt = 9` it
is paid. Nobody retrained anything. A person changed a number in a rule, and can be asked why.

## Why a score and not a verdict

It would have been easy to expose this differently. The vocabulary could have offered
`SHOULD_APPROVE(claim) -> BOOLEAN`, and the rule would have been one line long.

That version is worse, and the reason is not about accuracy.

With a score, the policy — what counts as too unusual — lives in the rule. It is visible, it is
version-controlled, it is reviewable by someone in finance, and when the auditor asks why this
claim was flagged and that one was not, the answer is a line somebody wrote on purpose.

With a verdict, the policy lives in the model. It is not visible, not reviewable, and not
explicable. The auditor's question has no answer beyond "the model said so." And your BUBAS
program, which existed so that a subject-matter expert could own the rule, has been reduced to
glue between a model and a database.

Generalised, and worth remembering when you are asked to add an operation:

> **Advisory outputs — scores, classifications, extractions — leave the decision with the expert.
> Verdict outputs move it to the model.**

That is not an argument against models. It is an argument about where the seam goes.

## What it costs

An operation that will not give the same answer twice has consequences, and they should be stated
plainly rather than discovered.

**A rule using it is no longer reproducible.** Run it on the same claim next week and it may decide
differently, because the model changed even though the rule did not. Every other operation in this
book is stable in a way this one is not.

**Calls cost something.** A model behind an operation may take a second and may cost money per
call. Look again at the rule above: it calls `ANOMALY_SCORE_OF` twice for every line, once to
compare and once to store. On a claim with twenty lines that is forty calls where twenty would do.
With `TOTAL_OF` nobody would care. Here it is worth assigning the score to a variable first — one
of the few places in this book where how a rule is written affects anything but its readability.

**It can fail in a way arithmetic does not.** A service can be slow, or down, or answer nonsense.
What should the rule do then? That question belongs to whoever exposes the operation, and chapter 30
is about the two audiences for the answer.

**And it cannot be tested by running it.** This is the big one, and it is the bridge to the next
part of the book.

## The thing Part 2 is about

Every test you might write for the rule above runs into the same wall. You cannot assert that a
claim is escalated, because you cannot know what the model will say about it. You cannot even assert
that it is *not* escalated, for the same reason.

The usual advice — mock the things your code depends on — is advice that most people quietly ignore
for databases, because you can just run a database. Here you cannot just run the model. Mocking
stops being tidiness and becomes the only way this rule can be checked at all.

And what you are checking, once the score is pinned to a known value, is not the model. It is your
rule: that a score of 8 is escalated when the threshold is 8, that a score of 7 is not, that the
worst line wins rather than the first. Those are policy questions with right answers, and they are
exactly what the person who owns the policy would want checked.

Part 2 is how.

## The honest limit

One thing this chapter should not leave you believing.

The vocabulary bounds what a program can *name*. It does not bound what a named thing *does*. An
operation described as returning an opinion about a line of spending could, behind that boundary,
send the whole claim to a third party, log the claimant's name somewhere it should not be, or cost a
euro a call. The description is what somebody wrote; the behaviour is what somebody built.

This is true of every operation in the book, and it has been said before. It is worth repeating
here because a model-backed operation is the one where the gap between the description and the
behaviour is largest, hardest to inspect, and most likely to change without anybody editing a line
of code. The review checksum that chapter 11 mentions will not notice. Nothing in BUBAS will.

## What is coming

You have now met every kind of operation a BUBAS vocabulary can offer. The last chapter of this
part is about the vocabulary itself: how to find out what a language you have been handed can
actually do, and what to do when it cannot do what you need.
