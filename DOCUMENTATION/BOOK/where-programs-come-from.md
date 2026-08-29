# Where programs come from

<!-- abstract -->
Rules have to be written, stored, versioned, reviewed and deployed by people who are not using your
source control. Authoring, storage, review workflow built on the export, and rolling back a rule
that turned out to be wrong.
<!-- /abstract -->

---

## The question the language does not answer

BUBAS compiles a string. Where the string comes from is your problem, and it is the problem that
decides whether any of this works in practice.

If rules live in your repository and change through pull requests, you have not gained much — the
person who owns the policy still cannot change it without an engineer. That arrangement is
legitimate and simpler, and plenty of teams should stop there. But the argument of chapter 1 only
fully pays off when the rule-writer can write.

## Authoring

The minimum useful editor is a text box, a Save button, and the compiler's message displayed on
failure. That is genuinely enough to start, because chapter 30's diagnostics are written for this
audience.

What repays effort after that, roughly in order:

**The vocabulary document beside the editor.** Chapter 11's export, on the same screen. A rule
writer's commonest question is "what can I say", and the answer already exists in a form they can
read.

**Compile on keystroke.** The compiler is fast enough, and there is a large difference between
finding out on save and finding out as you type.

**Never store a rule that does not compile.** Chapter 30 made this point; it belongs here as a
storage invariant. Every stored rule compiles, so deployment can never fail for that reason.

What does not repay effort is a visual builder. Blocks and dropdowns look friendlier and are worse:
they are harder to review, harder to diff, harder to search, and they hide the artefact that the
whole design exists to make readable. The reason a rule is eleven readable lines is so that people
can read it.

## Storage and versioning

Rules are content, and want what content wants: a store, a history, and an identity that survives
edits.

Three properties are worth insisting on.

**Every version is kept.** When a claim was decided in March, the question "what did the rule say
then" must have an answer. Overwriting destroys the audit trail that chapter 30's decision log
depends on.

**Decisions record which version decided them.** A rule identifier and a version, stored with the
outcome. Without it the decision log says what happened but not why, and the two drift apart the
first time a rule changes.

**Rolling back is a deploy, not an edit.** Activating an earlier version should be one action with
its own record — not somebody pasting old text into the editor, which produces a new version that
merely resembles the old one.

None of this is exotic; it is what any content system does. It is worth stating because teams reach
for a database column and discover the requirements one incident at a time.

## Review

This is where the export earns its keep, and where the workflow differs from code review.

**Review the vocabulary once, before the rules exist.** Chapter 26 argued this and it is the step
people skip. A vocabulary agreed at the start is a set of operations somebody with domain authority
confirmed the domain has. Reviewed after fifty rules depend on it, review can only ratify.

**Review rules the way you review policy, not the way you review code.** The reviewer is checking
whether the rule says what the policy says. They need the rule, the policy clause, and the tests —
and because of Part 2, they can read all three.

**Make the tests part of the review.** A rule arriving without cases for its thresholds is a rule
whose author has not thought about the boundaries. Chapter 20's list is a reasonable standard to
hold people to.

## Deployment

The useful property, and the reason to keep rules out of the application artefact: **a rule change
is not a deployment**.

A new rule version compiles, its tests run, and it becomes active — without rebuilding or restarting
anything. Chapter 29 explains why that is safe: a `BubasProgram` is immutable, so replacing a map
entry needs no coordination and no request sees a half-updated rule.

Two things to build in from the start.

**Run the rule's tests before activating it.** You have a test framework the rule-writer can read;
running the suite on save and refusing activation on failure is a small amount of plumbing for a
large amount of safety.

**Make activation reversible in one action.** The previous version is compiled and sitting in the
store. Going back should take seconds and leave a record, because the moment you need it you will
not want to be reading this chapter.

## What good looks like

A rule-writer opens an editor, sees the operations available, writes a rule, sees it compile as they
type, writes three cases for its thresholds, sees them pass, and sends it for review. A reviewer
reads the rule against the policy, reads the cases, and approves. Activation happens without a
deployment, and the decision log from that moment names the version.

Nothing in that requires an engineer, and every step is something a person can be held to. That is
the arrangement this book has been building toward, and it is worth noticing that the language was
only ever part of it.
