# A first test

<!-- abstract -->
A complete test of the rule from chapter 2: the claim it is given, what it should decide, and what
the runner prints when it agrees and when it does not. Enough to write tests for rules you already
have.
<!-- /abstract -->

---

## The whole test

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

Fourteen lines, testing the eleven-line rule from chapter 2. Taken apart below, in the order it
runs.

## The name is a sentence

<!--INCLUDE
from: "../../bubas-doc/src/test/resources/bunit/under-the-limit-is-approved.bu"
start:
  pattern: 'PROGRAM UnderTheLimit'
  include: true
end:
  pattern: '"TOTAL_OF"'
  include: false
prefix: "```"
postfix: "```"
margin: 0
_content_generated_: 41:md5:328374034881025a4f08efc004bd7a18
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```
PROGRAM UnderTheLimitIsApproved
```
<!--/INCLUDE-->

A test is a BUBAS program like any other, and its name is what the report prints. Name it after the
case, not after the rule: `UnderTheLimitIsApproved` tells a reader what failed when it fails, where
`TestApproveExpense1` tells them nothing.

## Standing in for the world

<!--INCLUDE
from: "../../bubas-doc/src/test/resources/bunit/under-the-limit-is-approved.bu"
start:
  pattern: '"TOTAL_OF"'
  include: true
end:
  pattern: '"REJECT _, _" IS MOCKED'
  include: true
prefix: "```"
postfix: "```"
margin: 0
_content_generated_: 108:md5:232f276f8252bc020179c6f70e2ff8c6
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```
"TOTAL_OF" WITH ARGS("Alice's claim") RETURNS 128.40
"APPROVE _" IS MOCKED
"REJECT _, _" IS MOCKED
```
<!--/INCLUDE-->

The rule calls three operations. In a test none of them may really happen — `TOTAL_OF` would want a
real claim, and `APPROVE` would pay somebody — so each is replaced.

`TOTAL_OF` is replaced by an **answer**: asked about Alice's claim, say 128.40. That is the case
this test is about.

`APPROVE` and `REJECT` answer nothing, so there is nothing to say except that they are stood in for.
`IS MOCKED` means *let this be called, do nothing, and remember that it was*.

The quoted names are the shapes from the vocabulary. A function is its name; a statement is its
name with `_` for each thing it takes, which is exactly how the vocabulary document of chapter 11
lists it. `REJECT` takes two, so it is `"REJECT _, _"`.

## What the rule is given

<!--INCLUDE
from: "../../bubas-doc/src/test/resources/bunit/under-the-limit-is-approved.bu"
start:
  pattern: 'ARGUMENT "claim"'
  include: true
end:
  pattern: 'ARGUMENT "limit"'
  include: true
prefix: "```"
postfix: "```"
margin: 0
_content_generated_: 72:md5:99479a01fc974e65361e0c5cba1cf943
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```
ARGUMENT "claim" IS "Alice's claim"
ARGUMENT "limit" IS 200.00
```
<!--/INCLUDE-->

One line per parameter, in the rule's own words.

`limit` is a decimal and reads as one. `claim` is a `Report` — one of those values chapter 5 said a
program can hold but never open — and a test cannot construct one either. So it names one instead.
`"Alice's claim"` is a label for a claim that does not exist, which is enough, because the rule
never looks inside it. The next chapter is about why that works.

Note that the same label appears in the mock above. That is not decoration: it is how the test says
*when you are asked about this particular claim, answer 128.40*.

## Running it

<!--INCLUDE
from: "../../bubas-doc/src/test/resources/bunit/under-the-limit-is-approved.bu"
start:
  pattern: '^\s*RUN$'
  include: true
end:
  pattern: '^$'
  include: false
prefix: "```"
postfix: "```"
margin: 0
_content_generated_: 13:md5:bb02b8f3e64a6bc0fe1d5b07b0a00c30
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```
RUN
```
<!--/INCLUDE-->

Everything above describes the world. `RUN` is the moment the rule executes. Everything below is
about what happened.

The order is not a style rule. An expectation written before `RUN` is refused, because it would be
asking about something that has not happened — chapter 18 is about that check and the others like
it.

## What should be true afterwards

<!--INCLUDE
from: "../../bubas-doc/src/test/resources/bunit/under-the-limit-is-approved.bu"
start:
  pattern: 'RESULT IS TRUE'
  include: true
end:
  pattern: 'END\.'
  include: false
prefix: "```"
postfix: "```"
margin: 0
_content_generated_: 103:md5:14a0d6829980fa1af81302050d84bea2
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```
RESULT IS TRUE
"APPROVE _" WAS CALLED WITH ARGS("Alice's claim")
"REJECT _, _" WAS NOT CALLED
```
<!--/INCLUDE-->

Three claims about the run.

`RESULT IS` checks the answer the rule returned.

`WAS CALLED WITH ARGS(...)` checks that a particular thing happened, to a particular claim. Naming
the claim matters more than it looks: a rule that approves *something* is not the same as a rule
that approves *this*, and on a rule handling two claims the difference is the whole test.

`WAS NOT CALLED` checks that something did not happen. This is the half people leave out, and it is
often the more valuable one — a rule that approves and *also* refuses has a bug that no amount of
checking the approval would find.

## When it passes

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

Three tests of the same rule: under the limit, over it, and exactly on it.

That third one is there because somebody had to decide. Is a claim of exactly 200.00 against a
limit of 200.00 approved? The rule says `IF total > limit THEN` refuse, so it is approved — but the
policy document might have meant otherwise, and until somebody writes the case down, nobody knows
whether the rule matches the policy or merely matches itself.

## When it fails

Suppose somebody believed the limit was meant to be exclusive, and wrote the case that way:

<!--INCLUDE
from: "../../bubas-doc/target/doc-outputs/bunit-failing-test.txt"
prefix: "```"
postfix: "```"
_content_generated_: 129:md5:17140f67ef83e1cf6e598f79647e63de
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```
0/1 passed
FAILED TheLimitIsInclusive
line 11: expected the result to be false, but it was true
        RESULT IS FALSE
```
<!--/INCLUDE-->

The report names the test, the line, what was expected and what happened. Nothing about stack
frames or which framework noticed.

And note what this failure actually is. Neither the rule nor the test is broken; they disagree
about policy. The rule treats the limit as inclusive, the test expects exclusive, and the failure
is the disagreement surfacing where somebody can settle it. That is the most useful kind of test
failure there is, and it is only available because both sides are readable by the person who can
settle it.

## Enough to start

You can now write tests for rules you already have: name the case, answer the questions the rule
asks, give it its arguments, run it, and say what should be true.

What you cannot yet do is describe a world more complicated than one claim and one answer — several
calls to the same operation, arguments you only partly care about, operations that write values
back. The rest of this part is those.
