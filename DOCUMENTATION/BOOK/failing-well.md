# Failing well

<!-- abstract -->
Two kinds of failure with two audiences. A compile error is shown to the person writing the rule
and should name their line; a runtime error is an operations problem and should never be shown to
them at all. What to log, what to surface, and what to do with a program that will not compile.
<!-- /abstract -->

---

## Two failures, two audiences

Everything that can go wrong falls into one of two boxes, and confusing them is how an application
ends up showing a stack trace to somebody in finance.

**A rule that will not compile** is a problem for whoever wrote the rule. It happens when they save
it, before anything runs, and the message is about their line.

**A rule that fails while running** is a problem for whoever operates the system. It happens on a
particular claim, in production, and the rule-writer usually cannot do anything about it.

They arrive as the same Java exception type, which is why it is worth being deliberate.

## Compile failures belong to the author

`compile` throws a `BubasException` carrying the line, the source line and a diagnostic:

<!--INCLUDE
from: "../../bubas-doc/target/doc-outputs/stage3-top-tested.txt"
prefix: "```"
postfix: "```"
_content_generated_: 135:md5:5a96df68f92069a616cfc41dd4eaac7a
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```
line 14: 'lastMerchant' is read before it is assigned (at 14:24)
        NOTE "last was " + lastMerchant + ", limit " + limit
```
<!--/INCLUDE-->

Show that. All of it, unedited, next to the editor the author is typing in.

The temptation is to wrap it — "the rule could not be saved, please contact support" — and it
destroys the thing chapter 8 built. That message names a variable, a line, and what is wrong with
it, in words a non-programmer can act on. It is the entire feedback loop that lets somebody in
finance write a rule at all.

Three things worth doing around it:

**Compile on save, not on deploy.** A rule that fails to compile should fail in front of the person
who wrote it, seconds after they wrote it.

**Never store a rule that does not compile.** If it cannot be stored, it cannot be deployed by
accident, and the invariant "every stored rule compiles" is worth a great deal later.

**Do not translate the message.** Rewording it into your own vocabulary means maintaining a mapping
that will drift, and the originals were written for this audience.

## Runtime failures belong to operations

A `BubasException` from `run` means something went wrong on this claim: a division by zero, an
index past the end of an array, an overflow, or a handler that threw.

That last one is the common case, and it is worth seeing clearly. When `TOTAL_OF` cannot reach the
claim store, the failure surfaces as a run failure of the rule — but the rule is not wrong, and its
author cannot fix it. Showing them anything is a mistake.

So: log it with everything needed to diagnose — the rule, the claim, the diagnostic, the cause —
and give the caller whatever your application gives when something breaks. The rule-writer hears
about it only if the rule really is at fault, and telling them apart is a judgement your application
makes, not one BUBAS can make for you.

## What to log

The decision trail and the failure trail are different things and want different destinations.

**Decisions** are what the rule recorded through `ctx.log` — approved, refused, escalated, and why.
This is the audit trail. It should be durable, queryable, and joined to the claim, because in a
year somebody will ask why this claim was refused and the answer has to exist.

**Failures** are exceptions. These go wherever your other exceptions go.

Keep them apart. A decision trail with stack traces in it is not a decision trail, and an error
monitor full of ordinary refusals is an error monitor nobody reads.

## Failing inside a handler

You decide what a handler does when the world does not cooperate, and the choice is not obvious.

**Throwing** stops the run. The claim is not decided, the caller sees a failure, and nothing
partial has happened — except for whatever earlier statements already did, which is the wrinkle.
BUBAS has no transactions, so a rule that approved and then failed while notifying has approved.

**Returning a conservative value** lets the rule continue on an assumption nobody wrote down. A
score of 1 when the model is unreachable means every claim looks ordinary during an outage.

There is no general answer, and the harm is in choosing silently. Whichever you pick, say so in the
operation's description — chapter 26's document is where a rule-writer will look — and log the
substitution when it happens, so that "why did nothing get flagged last Tuesday" has an answer.

## The failure that is not a failure

One thing to get right in the plumbing: a rule returning `FALSE` is not an error.

Every rule in this book answers `FALSE` when a claim is not approved. That is a decision, and a
correct one. An application that treats a falsy return as a failure — retrying, alerting, or
raising — will alert on every refused claim, and the alerts will be turned off within a week.

Obvious written down; less obvious at four in the afternoon when wiring the caller.
