# Tokens

<!-- abstract -->
Standing in for opaque values you cannot construct; naming rather than building.
<!-- /abstract -->

---

## A value a test cannot make

The rule from chapter 2 needs a claim. A test has no way to make one.

That is not an oversight. Chapter 5 established that a domain value is sealed: a program can hold
one, pass it, and ask questions about it, and there is no syntax anywhere in BUBAS for building one
or looking inside. A test is a BUBAS program, so a test cannot build one either.

The way out is to notice what a rule can actually observe about a claim. It can pass it to
operations, and it can tell whether two of them are the same one. That is the entire list. Anything
else it might want to know is a question it has to ask, and in a test the answer to every such
question comes from a mock.

So the claim itself never needs to exist. It needs a **name**.

<!--INCLUDE
from: "../../bubas-doc/src/test/resources/bunit/under-the-limit-is-approved.bu"
start:
  pattern: 'ARGUMENT "claim"'
  include: true
end:
  pattern: 'ARGUMENT "limit"'
  include: false
prefix: "```"
postfix: "```"
margin: 0
_content_generated_: 45:md5:7f47f329ef869cbe2c45d2b75cfe5bba
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```
ARGUMENT "claim" IS "Alice's claim"
```
<!--/INCLUDE-->

`"Alice's claim"` is a token: a stand-in for a claim, identified by what the test called it. There
is no claim anywhere. There is a label, and everything the rule does with it is arranged by the
test.

## The rule that makes it work

A string written where an opaque value belongs names a token.

That is the whole rule, and it holds everywhere: in `ARGUMENT`, in what a mock returns, in what a
mock matches on, in what an expectation checks. It is unambiguous because opaque values are the one
kind BUBAS cannot construct, so a string in that position could not have meant anything else.

<!--INCLUDE
from: "../../bubas-doc/src/test/resources/bunit/under-the-limit-is-approved.bu"
start:
  pattern: '"TOTAL_OF" WITH ARGS'
  include: true
end:
  pattern: '"APPROVE _" IS MOCKED'
  include: false
prefix: "```"
postfix: "```"
margin: 0
_content_generated_: 62:md5:18a035073746b786dfd59c2482821291
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```
"TOTAL_OF" WITH ARGS("Alice's claim") RETURNS 128.40
```
<!--/INCLUDE-->

Both halves of that line use it. The argument matched on is a token — *when asked about the claim
called this* — and if `TOTAL_OF` returned a claim rather than a decimal, the answer would be one too.

## Tokens flow

A token handed to a rule comes back out the other side, and the framework keeps track:

<!--INCLUDE
from: "../../bubas-doc/src/test/resources/bunit/under-the-limit-is-approved.bu"
start:
  pattern: '"APPROVE _" WAS CALLED'
  include: true
end:
  pattern: '"REJECT _, _" WAS NOT CALLED'
  include: false
prefix: "```"
postfix: "```"
margin: 0
_content_generated_: 59:md5:896d3c447145b734e6def8a173d16eca
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```
"APPROVE _" WAS CALLED WITH ARGS("Alice's claim")
```
<!--/INCLUDE-->

That is not merely checking that something was approved. It checks that *this* claim was — the one
the test named and handed in. The rule passed it through `TOTAL_OF`, past a comparison and into
`APPROVE`, and it arrived as the same thing it started as.

## Two claims of the same kind

The interesting case is more than one. A rule that compares two claims and acts on one of them has
a bug worth catching: acting on the wrong one.

<!--INCLUDE
from: "../../bubas-doc/src/test/resources/bunit/two-claims-told-apart.bu"
prefix: "```"
postfix: "```"
_content_generated_: 435:md5:a332aef6fda1110b7b8a0136360c91fc
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```
PROGRAM TwoClaimsToldApart
    "TOTAL_OF" WITH ARGS("the small claim") RETURNS 40.00
    "TOTAL_OF" WITH ARGS("the big claim")   RETURNS 900.00
    "APPROVE _" IS MOCKED
    "REJECT _, _" IS MOCKED

    ARGUMENT "claim" IS "the big claim"
    ARGUMENT "limit" IS 200.00

    RUN

    RESULT IS FALSE
    "REJECT _, _" WAS CALLED WITH ARGS("the big claim", ANYTHING())
    "TOTAL_OF" WAS CALLED WITH ARGS("the big claim")
END.
```
<!--/INCLUDE-->

<!--INCLUDE
from: "../../bubas-doc/target/doc-outputs/bunit-tokens.txt"
prefix: "```"
postfix: "```"
_content_generated_: 46:md5:1608f48129a6fb4e48f11c4575c0b5e6
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```
1/1 passed
PASSED TwoClaimsToldApart
```
<!--/INCLUDE-->

Two tokens of the same type, coexisting. The mocks answer differently for each, and the expectation
names which one was refused. Nothing here depends on the order the rule happened to ask in — the
identification is by name, so a rule that asked about the small claim first would still be tested
correctly.

## Names are for reading

A token's name has no meaning to anything. `"o1"` would work as well as `"Alice's claim"`.

Use the second. The name appears in the call log and in every failure message, and the difference
between

<!--INCLUDE
from: "../../bubas-doc/target/doc-outputs/bunit-wrong-token.txt"
prefix: "```"
postfix: "```"
_content_generated_: 228:md5:7c91a49996c31d77f2459ee730988629
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```
line 13: expected REJECT _, _ to be called with ("the small claim", anything), but it was called with ("the big claim", "over the 200.00 limit")
        "REJECT _, _" WAS CALLED WITH ARGS("the small claim", ANYTHING())
```
<!--/INCLUDE-->

The same sentence with `o1` and `o2` in it is a puzzle rather than a diagnosis, and the good
names cost nothing.

## Where it stops

Two limits worth knowing before you meet them.

**A token that reaches a real handler fails.** A token is not an instance of the Java class behind
the type, so an operation you left unmocked will be handed something it cannot use. The error says
so, but the fix is the previous chapter's rule: an opaque-valued surface is mocked as a whole.

**Some tokens the framework supplies for you.** When a mocked command writes into an opaque
variable — `ROUTE claim TO approver AT centre` — nothing in the test needs to name the cost centre,
because a token is the only thing it could be, and the framework provides one. You will see it in
the call log as `"#1"`. What a command writes into a *non*-opaque variable is a different matter, and
the next chapters take it up.
