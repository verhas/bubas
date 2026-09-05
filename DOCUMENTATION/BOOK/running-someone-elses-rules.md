# Running someone else's rules

<!-- abstract -->
Everything left: resource limits, what they now bound and what they still do not, auditing the
decisions a rule made and why, observability, and what containment untrusted input still needs.
Ends with the complete application assembled.
<!-- /abstract -->

---

## Three degrees of trust

"Someone else's rules" covers three situations that need different things, and conflating them
leads either to paranoia or to a bad afternoon.

**Your colleagues' rules.** People in your organisation, accountable, reviewed. This is what the
book has assumed throughout, and nothing in this chapter's harder half applies.

**Rules from a model.** Chapter 32. Reviewed by a person before activation, so the trust question
is really a review-capacity question — with one exception, below.

**Rules from outside.** A customer writing rules in your product, a tenant configuring their own
approval policy. Nobody reviews these. This is the case BUBAS is not yet ready for, and saying so
is more useful than implying otherwise.

## The gap, stated plainly

Some of what this chapter used to call missing is now there, and the part that remains is the harder
part.

**A run can be bounded.** `maxSteps` stops a program that loops forever; `maxArrayLength` refuses the
single enormous `DECLARE lines[n] Item`. Both are per run, both default to off, and a rule that has
never been given a budget still runs until it finishes. Chapter 28 shows the two lines.

**There is still no deadline.** The budget counts steps the program takes, and a call into an
operation you registered is one step however long it blocks. An untrusted rule cannot spin, but it
can call `LOAD_CLAIM` in a loop and make your database do the spinning.

**The memory bound is per array.** It stops one absurd allocation, not a thousand ordinary ones.

**And nothing bounds what an operation does.** This is the real remainder, and no limit on the
interpreter reaches it: the vocabulary is your code, and a rule can only call what you exposed. That
is a design property rather than a gap, but it means the budget protects you from the *program* and
not from the *vocabulary*.

So an untrusted rule still needs a thread you watch from outside, with a hard decision about what to
do when it will not stop — but the decision now arrives for a narrower set of reasons, and a rule
that merely loops is no longer one of them. A process boundary is still more honest if the scale
justifies it.

The honest summary has moved by one word: **BUBAS bounds what a rule can name, and now bounds some
of what it can consume.** For the first two degrees of trust that was already enough. For the third
it is closer, and the remaining distance is a deadline and a way to bound the operations
themselves.

## Auditing decisions

If a rule decided something about a person, that decision will be questioned, and the question
arrives long after everyone has forgotten the details.

Five things make the answer available. Store them together, with the outcome:

- **The claim** — what was decided about
- **The rule identifier and version** — chapter 31's requirement, and the one people skip
- **The decision and its stated reason** — what `APPROVE` and `REJECT` logged
- **The inputs the application supplied** — the limit, the flags, whatever varied per run
- **What non-deterministic operations answered** — chapter 25's requirement, and the only way an
  anomaly score is ever explicable

With those, "why was this refused in March" is a query. Without the version, you have what happened
and not why. Without the model's answer, you cannot explain the one decision most likely to be
challenged.

A useful property falls out of the design: the reason strings in the decision log are the same
sentences a rule-writer wrote and a reviewer read. The explanation you give an auditor is not a
reconstruction — it is the artefact.

## Observability

Three things worth measuring, and they are not the usual three.

**Compile failures per author.** Not an error rate — a usability signal. A rule-writer failing
repeatedly on the same diagnostic has found either a gap in the vocabulary or a message that does
not say what it means. Both are worth knowing.

**Which operations are actually called.** The vocabulary is finite, so this is a complete list, not
a sample. Operations nobody calls are either missing a use case or should be removed; an operation
suddenly called ten times more often is a policy change somebody made.

**Decision mix over time.** Approvals, refusals and escalations as proportions. A rule change that
shifts the mix is doing something, and if nobody expected it to, that is the alert worth having.
This is far more useful than latency, which is not going to be the problem.

## The complete picture

Everything the book has built, in the order it runs:

**At startup.** Build the vocabulary and seal it. Chapter 24's overlap analysis runs here, so an
ambiguous vocabulary fails the deployment rather than a claim. Compile whatever rules ship with the
application.

**When somebody writes a rule.** They see the vocabulary document beside the editor, the compiler
answers as they type, and a rule that does not compile is never stored. They write cases for the
thresholds; the cases run on save.

**When somebody reviews it.** The person who owns the policy reads the rule against the policy and
reads the cases. Both are in a language they read. Activation is recorded and reversible.

**When a claim arrives.** A fresh interpreter, the request's services, the arguments, one run. The
decision and its reason go to the audit store with the rule's version.

**When something goes wrong.** A compile failure reaches the author with its line intact. A runtime
failure reaches operations and not the author. The two trails stay separate.

**When the vocabulary changes.** The export is regenerated, the checksum says which descriptions
need re-reading, and somebody with domain authority reads them — ideally before the rules that
depend on them exist.

## What it was all for

A pull request arrives. A few hundred lines of Java implementing the new expense policy. It
compiles, the tests pass, and the person who owns the rule cannot read it.

That was chapter 1, and the arrangement in this book is one answer to it. The rule is eleven lines
in a language with no vocabulary of its own. The test is in the same language. The list of things
the rule can say is a document somebody agreed to before any of it was written. The reason a claim
was refused is a sentence the reviewer read.

None of that makes the rule correct. It makes the rule *reviewable by the person who knows whether
it is correct*, which is the only thing that was ever missing.
