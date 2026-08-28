# Why the test must be readable too

<!-- abstract -->
A rule reviewed by the person accountable for it needs a test that the same person can check.
A test written in a language they cannot read moves the review straight back to where chapter 1
found it. BUNIT tests are written in BUBAS for exactly this reason.
<!-- /abstract -->

---

## The review, one step further on

Chapter 1 described a review that did not happen: a rule nobody accountable could read, approved by
somebody who could only check that it looked reasonable. Part 1 fixed that. The rule is now
eleven lines that a finance manager can read and argue with.

Now ask the same question about the test.

A rule can be read and agreed with and still be wrong. Agreement means the reader followed what the
rule says; it does not mean they worked out what it does on a claim of exactly 200.00 against a
limit of exactly 200.00, or on a claim with no lines, or on the one that arrives at the end of the
budget year. Those are the cases that matter, and nobody finds them by reading.

So there is a second artefact — the test — and it carries the answers to precisely the questions
the reviewer could not answer by reading. If that artefact is written in Java, the review has moved
right back to where chapter 1 found it. The person who knows whether the threshold is inclusive
cannot check that anybody encoded it as inclusive.

## What a test looks like here

<!--INCLUDE
from: "../../bubas-doc/src/test/resources/bunit/under-the-limit-is-approved.bu"
prefix: "```"
postfix: "```"
_content_generated_: 345:md5:3e4cd06ef9a9358c98f4de8c420a654b
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```
PROGRAM UnderTheLimitIsApproved
    "TOTAL_OF" WITH ARGS("Alice's claim") RETURNS 128.40
    "APPROVE _" IS MOCKED
    "REJECT _, _" IS MOCKED

    ARGUMENT "claim" IS "Alice's claim"
    ARGUMENT "limit" IS 200.00

    RUN

    RESULT IS TRUE
    "APPROVE _" WAS CALLED WITH ARGS("Alice's claim")
    "REJECT _, _" WAS NOT CALLED
END.
```
<!--/INCLUDE-->

That is a complete, running test of the rule from chapter 2, and it is written in BUBAS. Not in a
BUBAS-flavoured configuration file, and not in something that generates BUBAS — in the language,
with the same statements, the same quoting, the same `END.`

You can read it for the same reason you could read the rule. The claim comes to 128.40, the limit
is 200.00, the answer should be yes, the claim should be approved, and nothing should be refused.

More to the point, you can *disagree* with it. Should a claim of exactly 200.00 pass? This test does
not say. Somebody should decide, and that somebody is not whoever typed the test.

## Reading a test is not the same as reading a rule

There is a real difference worth naming, because it is where the value is.

A rule tells you what the policy is. A test tells you what the policy **does on one specific
case**, chosen by somebody who was thinking about where it might go wrong. A suite of them is a
list of cases somebody thought about — which is exactly the artefact a reviewer wants and never
normally gets.

When the tests are readable, "have we covered the case where a claim is exactly at the limit?"
stops being a question for engineers and becomes a question anybody can answer by looking.

## The three things a test says

Every BUNIT test has the same shape, and it maps onto how anybody would describe a case out loud.

**What the world was like.** The claim came to 128.40. Nothing else about the world matters, so
nothing else is mentioned.

**What happened.** The rule ran, with this claim and this limit.

**What should be true afterwards.** It said yes, it approved the claim, and it did not refuse
anything.

Chapter 13 goes through it line by line. What matters here is that all three are in the language of
expense approval rather than the language of testing. There are no fixtures, no setup methods, no
builders, no assertion library. There is a claim, a limit, and what should have happened to them.

## Running them

<!--INCLUDE
from: "../../bubas-doc/target/doc-outputs/bunit-passing-suite.txt"
prefix: "```"
postfix: "```"
_content_generated_: 115:md5:e9c4e950e15be88e0fb06c13b5b70803
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```
3/3 passed
PASSED UnderTheLimitIsApproved
PASSED OverTheLimitIsRefused
PASSED ExactlyAtTheLimitIsApproved
```
<!--/INCLUDE-->

Three cases, three passes, and the names are sentences rather than method names. A report like that
can go in front of the person who owns the policy without translation, which is the whole argument
of this part in one screen.

## What this part covers

The next chapter takes a single test apart. After that:

**Standing in for the world.** Your rule calls operations that load claims, charge budgets and send
messages, and a test cannot have those really happen. What replaces them, and what that costs.

**Tokens.** A claim is a value you cannot construct. Tests name them instead.

**Matching arguments.** Saying what matters about a call and leaving the rest alone.

**Leaving some of it real.** When standing in for less makes a test more honest, and when it hides
what you meant to check.

**Mocks that cannot be right.** The framework refuses some tests before running them, and the
reasons are worth understanding rather than working around.

**Testing what cannot be tested.** The model-backed operation from chapter 10, which cannot be
tested by running it at all.

**A suite that stays honest.** What to cover, and a coverage claim that a finite vocabulary makes
possible.

Still no Java. The person who owns the rule can read every page of this part, and the engineer who
will embed the language needs all of it too.
