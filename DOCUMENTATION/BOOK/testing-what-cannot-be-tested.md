# Testing what cannot be tested

<!-- abstract -->
The model-backed operation from chapter 10 will not give the same answer twice, so there is no
version of this test that does not stand in for it. Mocking stops being good practice here and
becomes the only way the rule can be checked at all — and the rule around it, the threshold you
chose, is what you are really testing.
<!-- /abstract -->

---

## The wall

Chapter 10 added an operation that asks a model how unusual a line of spending looks. Everything
about writing rules with it was straightforward. Testing one is not.

Take the obvious test: a claim with a large unreceipted dinner should be escalated. Run it. It
escalates. Run it next month, after somebody retrained the model, and it does not. Neither run tells
you anything about your rule, because the input to the decision changed underneath it.

You cannot assert the claim is escalated. You cannot assert it is *not*. You cannot even assert the
test is stable enough to run twice.

This is the wall every argument for mocking eventually gestures at and rarely reaches. Nobody
mocks a database because they must — you can run a database. Here there is no version of the test
that works by running the real thing.

## What you are actually testing

Once you accept that the score has to be pinned, something clarifies.

You were never testing the model. You have no way to test the model from here, and it is not yours
to test. What you are testing is **the rule you wrapped around it**: that a score of 8 escalates
when the threshold is 8, that the worst line wins rather than the first, that a claim which trips
nothing still gets checked against the limit.

Those are policy questions with right answers, decided by a person, and they are exactly what the
person who owns the policy would want checked.

<!--INCLUDE
from: "../../bubas-doc/src/test/resources/bunit/a-flagged-line-is-escalated.bu"
prefix: "```"
postfix: "```"
_content_generated_: 674:md5:19a60dbce97914d000ec042c41ec4978
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```
PROGRAM AFlaggedLineIsEscalated
    "ITEM_COUNT" WITH ARGS("the claim") RETURNS 2
    "ITEM_AT" WITH ARGS("the claim", 1) RETURNS "the taxi"
    "ITEM_AT" WITH ARGS("the claim", 2) RETURNS "the dinner"
    "ANOMALY_SCORE_OF" WITH ARGS("the taxi")   RETURNS 2
    "ANOMALY_SCORE_OF" WITH ARGS("the dinner") RETURNS 8
    "TOTAL_OF" WITH ARGS("the claim") RETURNS 411.00
    "ESCALATE _, _" IS MOCKED
    "APPROVE _" IS MOCKED

    ARGUMENT "claim"  IS "the claim"
    ARGUMENT "limit"  IS 2000.00
    ARGUMENT "flagAt" IS 8

    RUN

    RESULT IS FALSE
    "ESCALATE _, _" WAS CALLED WITH ARGS("the claim", CONTAINS("scored 8"))
    "APPROVE _" WAS NOT CALLED
END.
```
<!--/INCLUDE-->

Two lines, two scores, one over the threshold. The rule should escalate and should not approve.

## The threshold is the thing

Here is the same test with one number changed — the threshold, not the score:

<!--INCLUDE
from: "../../bubas-doc/src/test/resources/bunit/the-threshold-is-the-rule.bu"
start:
  pattern: 'ARGUMENT "flagAt"'
  include: true
end:
  pattern: 'RESULT IS'
  include: false
prefix: "```"
postfix: "```"
margin: 0
_content_generated_: 38:md5:a929bbaa04fa55c6bd16f79864f8f3fa
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```
ARGUMENT "flagAt" IS 9

RUN

```
<!--/INCLUDE-->

The dinner still scores 8. The model's opinion has not changed. The claim is approved, because the
rule now flags at 9.

<!--INCLUDE
from: "../../bubas-doc/target/doc-outputs/bunit-model.txt"
prefix: "```"
postfix: "```"
_content_generated_: 80:md5:2051ec7b4fc29d12c6f073e6a090fa50
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```
2/2 passed
PASSED AFlaggedLineIsEscalated
PASSED TheThresholdIsTheRule
```
<!--/INCLUDE-->

Two tests, both passing, differing in one number that a person chose and can be asked about. That
pair is the argument of chapter 10 turned into something a build can check: the policy is in the
rule, and moving it changes outcomes in a way somebody is accountable for.

## Pinning a score is a claim about the world

One honest complication. When a test says the dinner scores 8, it is asserting something about a
model it cannot verify.

If the real model never returns 8 for anything resembling that line, the test is checking a case
that does not occur. It will pass forever and protect nothing — a subtler version of the
test-shaped object chapter 18 was about, and one no checker can catch.

There is no clean fix, only a practice. **Pin scores at the boundaries your rule cares about, not
at arbitrary numbers.** A rule that flags at 8 should be tested at 7, 8 and 9, because those are
the values where its behaviour changes. Whether the model ever produces 8 for a given line matters
much less than whether your rule does the right thing when it does.

That also keeps the tests useful when the model is replaced. The boundaries are yours; the
distribution is not.

## What the tests cannot tell you

Worth stating plainly, because a green suite is persuasive.

**Not whether the scores are any good.** A model that scores everything 5 would pass every test
here.

**Not whether the threshold is right.** The tests prove the rule does what it says at 8. Whether 8
is the correct place to draw the line is a policy question, decided by a person, and no test decides
it. What the tests do is make the decision visible and its consequences checkable — which is all
this book ever claimed for the arrangement.

**Not whether the operation is safe.** The vocabulary bounds what a rule can *name*, not what a
named thing *does*, and an operation backed by a service is where that gap is the widest. Chapter 32
takes it up.

## The shape this leaves you with

A rule that consults a model ends up with more tests than a rule that does not, and they are the
straightforward kind: for each threshold, a case just under, a case exactly on it, and a case just
over.

That is a good outcome. The unpredictable part has been pushed behind a boundary, and what is left
in front of it is a policy you can enumerate. The alternative — a rule where the model decides —
would have nothing to enumerate at all, which is chapter 25's argument seen from the testing side.
