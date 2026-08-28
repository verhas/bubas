# Matching arguments

<!-- abstract -->
Saying which calls a mock should answer: exact values, ranges, anything at all, anything but. How
much to pin down, and why pinning down everything produces a test that fails whenever anyone
touches anything.
<!-- /abstract -->

---

## Two places arguments are matched

Arguments come up twice in a test, and it is worth keeping them apart.

**On the way in**, a mock decides whether a call is the one it was set up for:

<!--INCLUDE
from: "../../bubas-doc/src/test/resources/bunit/two-claims-told-apart.bu"
start:
  pattern: '"TOTAL_OF" WITH ARGS\("the big claim"'
  include: true
end:
  pattern: '"APPROVE _" IS MOCKED'
  include: false
prefix: "```"
postfix: "```"
margin: 0
_content_generated_: 64:md5:f178a9a3c73b0f83ec495bdfc45a14fa
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```
"TOTAL_OF" WITH ARGS("the big claim")   RETURNS 900.00
```
<!--/INCLUDE-->

**On the way out**, an expectation checks a call that happened:

<!--INCLUDE
from: "../../bubas-doc/src/test/resources/bunit/two-claims-told-apart.bu"
start:
  pattern: '"REJECT _, _" WAS CALLED'
  include: true
end:
  pattern: '"TOTAL_OF" WAS CALLED'
  include: false
prefix: "```"
postfix: "```"
margin: 0
_content_generated_: 73:md5:caab745f742aeee95647fe1257c03598
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```
"REJECT _, _" WAS CALLED WITH ARGS("the big claim", ANYTHING())
```
<!--/INCLUDE-->

The same matching applies in both directions, which is deliberate — a test that could describe a
call one way and check it another would eventually disagree with itself.

## Exact values

Write a value and it must match. Text matches text, numbers match numbers, a name matches the token
it names.

`DECIMAL` compares by value rather than by how it was written, exactly as `=` does in the language,
so a mock answering `1.50` satisfies an expectation of `1.5`. That is the same rule chapter 3 gave,
applied in the one place where it would otherwise be surprising.

## Saying you do not care

Most calls have an argument the test has no opinion about. `ANYTHING()` says so:

<!--INCLUDE
from: "../../bubas-doc/src/test/resources/bunit/matchers.bu"
prefix: "```"
postfix: "```"
_content_generated_: 402:md5:3ad88fcd75b3367fe2d17fee19d7ce2c
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```
PROGRAM WhatMattersAboutTheRefusal
    "TOTAL_OF" WITH ARGS(ANYTHING()) RETURNS 1230.00
    "APPROVE _" IS MOCKED
    "REJECT _, _" IS MOCKED

    ARGUMENT "claim" IS "some claim"
    ARGUMENT "limit" IS 200.00

    RUN

    RESULT IS FALSE
    "REJECT _, _" WAS CALLED WITH ARGS(ANYTHING(), STARTS_WITH("over the"))
    "REJECT _, _" WAS CALLED WITH ARGS(ANYTHING(), CONTAINS("200.00"))
END.
```
<!--/INCLUDE-->

<!--INCLUDE
from: "../../bubas-doc/target/doc-outputs/bunit-matchers.txt"
prefix: "```"
postfix: "```"
_content_generated_: 54:md5:2a3dd0944e2a4e1bb8efaf5af4b5c4e1
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```
1/1 passed
PASSED WhatMattersAboutTheRefusal
```
<!--/INCLUDE-->

This test is about the *reason* a claim was refused, not about which claim it was. So the claim is
`ANYTHING()` in both the mock and the expectation, and the reason is checked two ways.

## Matching text

Refusal reasons are text built out of values, and pinning the whole string is usually the wrong
move. Three matchers cover most cases:

- `CONTAINS("200.00")` — the limit appears in the message somewhere
- `STARTS_WITH("over the")` — it begins the way the policy says it should
- `ENDS_WITH(...)`, and `MATCHES(...)` for a pattern when nothing simpler will do

The choice is a judgement about what the message is *for*. If the claimant is shown it and the
policy says the amount must appear, then `CONTAINS("200.00")` is the requirement, written down.
Pinning the entire sentence tests the wording, which nobody promised to keep.

## Matching numbers

`GREATER_THAN`, `LESS_THAN`, `BETWEEN`, `AT_LEAST`, `AT_MOST` do what they say, and are most useful
where a rule computes a figure your test should not have to reproduce. Asserting that an escalation
happened with a remaining budget `BETWEEN(2000.00, 3000.00)` says what you meant; asserting exactly
2500.00 says that plus an arithmetic claim you did not intend to make.

`ANYTHING_BUT(...)` is the odd one out and earns its keep: `ANYTHING_BUT("")` catches an empty
reason, which is the failure mode a message-building bug actually has.

## How much to pin down

This is the part that decides whether a suite is an asset or a liability, so it deserves a rule.

**Pin what the policy says. Leave everything else alone.**

A test that pins every argument of every call is a test that fails when somebody reorders two
harmless lines, rewords a message, or adds an argument nobody reads. Each of those failures costs
somebody an hour and teaches them that the suite cries wolf. After enough of them, people stop
reading failures and start re-running builds.

A test that pins too little passes when the rule is wrong, which is worse but at least obvious once
you look.

The way through is to ask, for each argument: *if this changed, would the policy have changed?* If
yes, pin it. If no, `ANYTHING()`.

## One thing you cannot say

There is `WAS CALLED`, `WAS NOT CALLED`, and `WAS CALLED WITH ARGS(...)`.

There is no `WAS NOT CALLED WITH ARGS(...)`. You can assert that an operation was never called at
all, and you can assert that it was called with particular arguments, but not that it was never
called with a particular set. If your test needs that — *the small claim must never have been
refused* — it has to be arranged some other way, usually by writing a case in which refusing it
would change the result.

Worth knowing before you write the line and find it does not parse.
