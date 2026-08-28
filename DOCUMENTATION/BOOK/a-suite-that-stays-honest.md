# A suite that stays honest

<!-- abstract -->
Organising tests so they keep their value: what to cover, what not to bother with, and the coverage
claim a finite vocabulary makes possible that a general-purpose language cannot — you can enumerate
every operation a rule is able to call.
<!-- /abstract -->

---

## The claim a small language lets you make

Ask what a piece of Java can do and there is no answer. It can call anything on the classpath, and
anything those call, indefinitely. Coverage is measured in lines executed because the set of
*things reachable* has no edge to measure against.

A BUBAS rule is different. Every operation it can call is in the vocabulary, and the vocabulary is
a list somebody wrote. So a question that is unanswerable elsewhere is arithmetic here:

**Which operations can this rule call, and which does my suite exercise?**

Both sides are finite and both are enumerable. That is not a coverage percentage; it is a complete
account, and it is worth building a suite around.

## What to cover

Three things, in order of how much they repay.

**Every branch out of the rule.** If it can approve, refuse or escalate, there is a test for each.
This is the minimum, and it is where a suite starts.

**Every threshold, three times.** Just under, exactly on, just over. The exactly-on case is the one
that finds real disagreements, because it is where the policy document and the rule most often part
company — chapter 13's inclusive-limit example is exactly this. If a rule has three thresholds,
that is nine tests, and they are cheap ones.

**Every operation the rule calls, in at least one test that leaves it real or checks its
arguments.** Not to test the operation, which is Java's job, but to notice when the rule stops
calling something it used to.

## What not to bother with

Equally important, and less often said.

**Combinations for their own sake.** Three thresholds do not need eight tests of every combination.
They need nine tests of the boundaries and one or two of realistic claims. Combinatorial suites
grow faster than the understanding they provide.

**The same case twice in different words.** Two tests that differ only in the claim's name are one
test.

**Wording.** Chapter 16's rule again: pin what the policy requires, not the sentence.

**Anything the compiler already refuses.** There is no value in a test asserting that a variable
must be assigned before use, or that a claim cannot be compared to another. Chapter 8's whole point
is that those are not runtime concerns. A suite that tests them is testing BUBAS, which somebody
else already did.

## Naming

The report is read by people who did not write the tests, and it prints names:

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

`UnderTheLimitIsApproved` says what case failed. `TestApproveExpense3` says a number. Name the case
in the words the policy uses, and a failing build tells somebody which rule is in dispute before
they open anything.

## One file per case

Keep each test in its own file, named after the case. It costs nothing, and it means a failure names
a file somebody can open, a rule change touches only the cases it affects, and two people can add
cases without meeting in the same file.

The alternative — a few large files grouping many cases — saves a little typing and costs the thing
that makes this whole arrangement work: that a subject-matter expert can be pointed at *one* file
and asked whether it is right.

## Keeping it honest over time

Suites rot in predictable ways, and two are worth guarding against deliberately.

**Tests that stopped meaning anything.** A rule changes, a test still passes because its mocks
happen to satisfy the new shape, and nobody notices it now exercises nothing. Chapter 18's checker
catches the mechanical cases. The rest is caught by the habit of deleting: when a rule changes,
read its tests and remove the ones that no longer describe a case anybody cares about. A suite you
prune is a suite people trust.

**Mocks that drifted from the operations.** Every mock is an assumption about what an operation
does. When the vocabulary changes — an operation's meaning shifts, a return type narrows — the
mocks do not automatically notice. The vocabulary document from chapter 11 is the thing to reread
when that happens, and the review checksum in chapter 26 is what tells you it happened at all.

## The end of Part 2

You can now write a rule, and check it, in a language the person who owns the rule can read. Both
artefacts — the policy and the evidence that it works — are reviewable by the same person, which is
the whole of what chapter 1 said was missing.

What has been assumed throughout both parts is that somebody built the vocabulary. Every operation
in these examples exists because a person decided this domain has that question and made it
askable, wrote its description, decided whether it returns a score or a verdict, and chose whether
it answers or does. Those decisions have shaped every page so far.

Part 3 is where they are made.
