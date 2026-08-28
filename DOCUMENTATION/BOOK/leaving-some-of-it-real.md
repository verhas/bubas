# Leaving some of it real

<!-- abstract -->
Not everything needs standing in for. Partial mocking, when leaving an operation real makes a test
more honest, and when it quietly hides the thing you meant to check.
<!-- /abstract -->

---

## Nothing says you must mock everything

A test mocks what it needs to control. Operations it says nothing about run for real.

Here is the rule from chapter 4 — the one that records notes as it decides — tested without
mocking `NOTE`:

<!--INCLUDE
from: "../../bubas-doc/src/test/resources/bunit/notes-are-left-real.bu"
prefix: "```"
postfix: "```"
_content_generated_: 278:md5:8b61ae050793b57fe04bcb4601acedfc
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```
PROGRAM NotesAreLeftReal
    "TOTAL_OF" WITH ARGS("Bob's claim") RETURNS 1230.00
    "APPROVE _" IS MOCKED
    "REJECT _, _" IS MOCKED

    ARGUMENT "claim" IS "Bob's claim"
    ARGUMENT "limit" IS 200.00

    RUN

    RESULT IS FALSE
    "REJECT _, _" WAS CALLED
END.
```
<!--/INCLUDE-->

`TOTAL_OF`, `APPROVE` and `REJECT` are stood in for. `NOTE` is not mentioned, so the real one runs,
and what it wrote is there afterwards:

<!--INCLUDE
from: "../../bubas-doc/target/doc-outputs/bunit-partial.txt"
prefix: "```"
postfix: "```"
_content_generated_: 128:md5:055aedc0e27f8a524f01d44c09425d5b
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```
1/1 passed
PASSED NotesAreLeftReal

what the rule wrote:
NOTE: checked against a limit of 200.00
NOTE: over by 1030.00
```
<!--/INCLUDE-->

The second line is arithmetic the rule did — 1230.00 over a limit of 200.00 — and no mock supplied
it. Had `NOTE` been mocked, that line would have vanished and the test would have been blind to a
rule that computed the overage wrongly.

## When leaving it real is right

**When the operation is the thing you want to observe.** A rule's notes and messages are often the
only externally visible evidence that it reasoned correctly. Mocking the operation that records
them throws that evidence away.

**When it has no side effects worth avoiding.** `NOTE` writes to a log the test can read. Nothing
is paid, nothing is sent, nothing is stored. There is no reason to replace it.

**When it is pure arithmetic over values the test already controls.** An operation that formats a
message or converts a currency, given inputs the mocks supplied, adds no unpredictability.

## When it is not

**When it touches the world.** `APPROVE` pays. `ROUTE` reads the approval policy from somewhere.
Anything that writes, sends, charges or asks a service gets mocked, and this is not a judgement
call.

**When it will not answer the same way twice.** Chapter 19's operation is the extreme case.

**When it needs a real domain value.** This is the one that catches people, and it follows from
chapter 15. A token is not a real claim, so an operation you left real will be handed something it
cannot use and will fail — not with a helpful message about mocking, but with a type error from a
handler.

The rule that follows is worth memorising: **an opaque-valued surface has to be mocked as a whole.**
If `ITEM_AT` is mocked and hands back a stand-in line, then `AMOUNT_OF`, `CATEGORY_OF`,
`MERCHANT_OF` and `HAS_RECEIPT` must be mocked too. Mocking half of it is the commonest mistake in
a first BUNIT test, and the framework goes out of its way to explain it when it happens.

## The honest question

There is a temptation to leave things real because mocking them is tedious, and to tell yourself
the test is more realistic for it. Sometimes that is true. Often it is the beginning of a test that
passes for reasons nobody understands.

The question to ask is: **is this operation part of what I am testing, or part of the world I am
describing?**

The rule under test is what you are testing. Everything it reaches for is the world. An operation
left real is a piece of the world you have decided not to control — which is fine when its
behaviour is obvious and its output is something you want to see, and a slow leak when it is
neither.

## A middle case worth naming

Some operations sit genuinely on the line. An operation that formats a claimant-facing message from
values your mocks supplied is arithmetic, and leaving it real tests it for free — but it is also
somebody else's code that could change under you, and a test that starts failing because a message
was reworded is the false alarm chapter 16 warned about.

The resolution is usually the same as there: leave it real, and assert on the part of the output the
policy actually requires. `CONTAINS("200.00")` survives a rewording; the whole sentence does not.
