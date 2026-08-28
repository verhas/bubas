# Mocks that cannot be right

<!-- abstract -->
A mock that sets no value the rule then reads, or answers a call the rule never makes, is not a
test — it is a test-shaped object that passes. What the consistency checker refuses, and why it is
deliberately narrow: a check that fires too widely destroys the thing it was protecting.
<!-- /abstract -->

---

## A test that passes and proves nothing

The worst outcome in testing is not a failing test. It is a test that passes without exercising
anything — green, reassuring, and empty.

BUNIT refuses a family of those before the rule ever runs. This chapter is what it refuses, and
more usefully, why the list stops where it does.

## A command that writes

Chapter 4 said some operations do rather than answer. A few do both: they perform something *and*
put a value into a variable for the rule to use afterwards.

The routing rule from stage 6 uses one. `ROUTE claim TO approver AT centre` reads the approval
policy once and produces two things — who has to sign, and which budget it charges — because those
are decided together and asking twice could get answers from two different readings.

Here is a test of it, and the calls it produced:

<!--INCLUDE
from: "../../bubas-doc/src/test/resources/bunit/over-limit-goes-to-a-manager.bu"
prefix: "```"
postfix: "```"
_content_generated_: 479:md5:67f49d2766cbf17864ef19afc4db99ff
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```
PROGRAM OverLimitGoesToAManager
    "TOTAL_OF" WITH ARGS("Erin's claim") RETURNS 450.00
    "ROUTE _ TO _ AT _" IS MOCKED
    "ROUTE _ TO _ AT _" SETS "approver" TO "the line manager"
    "BUDGET_LEFT" RETURNS 2500.00
    "ESCALATE _, _" IS MOCKED
    "APPROVE _" IS MOCKED
    "REJECT _, _" IS MOCKED

    ARGUMENT "claim" IS "Erin's claim"
    ARGUMENT "limit" IS 200.00

    RUN

    RESULT IS FALSE
    "ESCALATE _, _" WAS CALLED
    "APPROVE _" WAS NOT CALLED
END.
```
<!--/INCLUDE-->

<!--INCLUDE
from: "../../bubas-doc/target/doc-outputs/bunit-routing-passes.txt"
prefix: "```"
postfix: "```"
_content_generated_: 261:md5:d4150a7756d10db93e57865f4ec847be
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```
1/1 passed
PASSED OverLimitGoesToAManager

what the rule was given:
TOTAL_OF("Erin's claim")
ROUTE _ TO _ AT _("Erin's claim", "the line manager", "#1")
BUDGET_LEFT("#1")
ESCALATE _, _("Erin's claim", "needs the line manager, 2500.00 still available")
```
<!--/INCLUDE-->

Two things in that call log are worth slowing down for.

The approver reads `"the line manager"` — the test supplied it, with `SETS`. A mocked command does
not run its own handler, so nothing else could have written it.

The cost centre reads `"#1"`. Nobody wrote that. It is a token the framework supplied, because
`centre` is an opaque variable and a token is the only thing it could possibly be — the rule cannot
look inside it, and every operation that would have is mocked anyway. So the test says nothing about
it, and one flows on into `BUDGET_LEFT`.

**That is the rule, and it is worth stating as one:** an opaque target is filled in for you;
anything else you must supply.

## What happens if you forget

Take the `SETS` line away and the test is refused:

<!--INCLUDE
from: "../../bubas-doc/target/doc-outputs/bunit-unsupplied.txt"
prefix: "```"
postfix: "```"
_content_generated_: 191:md5:54a34e60096443569be2165f3b8929f0
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```
line 10: 'ROUTE _ TO _ AT _' is mocked, so its handler will not write 'approver' — and nothing supplies it on every path. Add: "ROUTE _ TO _ AT _" SETS "approver" TO ...
        RUN
```
<!--/INCLUDE-->

Notice three things about that message.

It names **what** is unset, **why** nothing will set it — the command is mocked, so its handler will
not run — and **what to write instead**, in the syntax you need. It also fires at `RUN`, not at the
expectation that eventually reads a garbage value, which is where the confusion would have been.

And notice what would have happened without the check. The rule would have read an unset variable,
and either failed with something baffling or, worse, worked by accident and left you with a passing
test of nothing.

## The other refusals

The same checker enforces a handful of related things, all with the same shape: *this test cannot
be meaningful, so it will not be run.*

**An expectation before the run.** Asking what a rule did before `RUN` is asking about something
that has not happened. The message says so.

**An expectation on an operation that was never mocked.** You cannot ask whether `APPROVE` was
called if nothing was watching. Declaring it mocked is what starts the watching.

**A mocked command declared but never reachable.** A mock for something the rule cannot call on any
path is either a typo or a leftover from a rule that changed. Both are worth knowing about.

**`SETS` on a command that is not mocked.** Then the real handler writes the variable, and what
you supplied would be silently ignored. The message says exactly that: *supplying it would be
ignored*.

## Why it stops there

The checker could do much more, and deliberately does not.

It could complain about a mock whose return value is never used. It could insist every mocked
operation is asserted on. It could require a `RESULT IS` in every test. Each of those catches a
real mistake sometimes, and each of them fires on perfectly good tests the rest of the time.

That trade is worse than it looks, because of what happens to a check that cries wolf. People
suppress it, or work around it, or stop reading its output — and then it is not protecting anything
while still costing everyone time. **A check that fires too widely destroys the thing it was
protecting.**

So the line is drawn at tests that *cannot* be meaningful, not tests that *look* careless. Every
refusal above is a case where the test would have been checking nothing, and the framework can
prove it. Where it cannot prove it, it says nothing and leaves the judgement to you.

## A useful side effect

Because the checker traces every path through a test before running it, it is also flow-sensitive in
a way you can lean on. A test can branch and loop like any BUBAS program, and the checker follows —
it will not accept a run that only happens on one path, or a value supplied inside an `IF` that the
rule might read outside it.

There is a pleasing consequence. A loop that drives several runs must be one whose body is
guaranteed to execute, for exactly the reason chapter 8 gave about `lastMerchant`: a top-tested loop
might run zero times, and the checker will not assume otherwise. The bottom-tested form works.
Nobody coordinated that; the checker and the language reached the same conclusion from the same
reasoning.
