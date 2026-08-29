# Programs written by a model

<!-- abstract -->
The generation case, end to end. What a finite vocabulary actually bounds, what it does not — an
operation you expose can do anything Java can do — and where the remaining risk sits. The
allow-list argument in full, with its limits stated.
<!-- /abstract -->

---

## The argument, restated where it can be examined

Chapter 1 made a case with nothing behind it yet: do not constrain a large language, supply a small
one. Thirty chapters later the small language exists, and the case can be stated precisely.

Ask a model for an expense rule in Java and you have granted the file system, the network,
reflection, process execution, and every class on the classpath. Nobody intended to grant any of it;
it arrives with the language. The guardrails people reach for — review the diff, run static
analysis, forbid imports, sandbox it — are all deny-lists over an unbounded space.

Ask for the same rule in a BUBAS vocabulary and the set of things the output can *name* is the list
you wrote. `DELETE_ALL_CLAIMS` is not rejected on policy grounds; it means nothing, and the program
does not compile, for the same reason a typo does not compile.

That is the whole mechanism. It is worth being exact about what it does and does not buy.

## What generation actually looks like

Four things to get right, and the first two matter most.

**Give the model the vocabulary document.** Chapter 26's `asJson()` exists for this. A model told
what operations exist, what they take and what they answer, produces programs that compile; one left
to guess produces plausible names that do not.

**Compile before you look.** The compiler is the first reviewer and it is free. Anything that does
not compile never reaches a person. Chapter 8's list — a value read before it is worked out, a path
that does not return, a type mismatch — is caught here without anybody's attention.

**Run the tests.** If the rule was requested along with cases, they run before a human sees
anything. A rule that compiles and fails its own cases is not worth reviewing.

**Then have a person read it.** Not an engineer — the person who owns the policy. That is the entire
point, and it is available because the output is eleven readable lines rather than two hundred of
Java.

The loop is worth stating as a shape: **generate, compile, test, review by the owner, activate with
a version.** Three of those five are automatic.

## What the vocabulary bounds

Precisely this: **the set of operations a generated program can name is finite, written down, and
was reviewed by a person before anything was generated.**

Everything else follows from it. A generated rule cannot reach a system nobody exposed. It cannot
grow a dependency on a field somebody is about to rename, because there are no fields. It cannot
smuggle behaviour past a reviewer in a helper method, because there are no helper methods. It
cannot do anything the vocabulary does not have a word for.

And the review that matters becomes possible. A finance manager can read a generated expense rule
and say whether it matches the policy — which is not a thing they can do with generated Java, at any
level of model competence.

## What it does not bound

Four limits. Overstating the mechanism is how the idea gets dismissed, so they are worth as much
space as the benefits.

**It bounds naming, not doing.** An operation you expose can do anything Java can do.
`RUN_SHELL_COMMAND` is a perfectly registrable operation. The vocabulary is exactly as narrow as the
operations you chose, and choosing badly gets you the exposure you were avoiding. The mechanism
moves the security decision from *reviewing output* to *designing a vocabulary* — earlier, once,
and by someone qualified, but it does not remove it.

**It does not make the rule correct.** A generated rule that compiles, passes its tests and reads
plausibly can still encode the wrong policy. That is what the human review is for, and no amount of
constraint substitutes for it.

**It does not bound resources.** Chapter 29 said this and it applies with force here: a generated
program can loop forever, and nothing stops it. For untrusted generation this is the gap that
matters most, and it needs containment outside the language.

**It does not bound what the operations are asked to do.** A rule that calls `APPROVE` on every
claim is fully within the vocabulary and entirely wrong. Damage does not require exotic capability;
it requires the ordinary capability applied wrongly, which is why the review is not optional.

## Where the risk actually sits

Ranked by how much they should worry you.

**Vocabulary design.** This is nearly all of it. An operation exposed carelessly is a permanent
grant, and the model will find it. Chapter 23's test — *does this encode a decision somebody could
disagree with?* — is the load-bearing one.

**Absent resource limits.** Real, unsolved, and the reason untrusted generation needs a thread you
can watch and abandon.

**Volume.** A model can produce fifty plausible rules in a minute, and review capacity is a person's
afternoon. The bottleneck moves to the review, and a team that responds by reviewing less has given
up the mechanism while keeping the paperwork. If you cannot review it, do not generate it.

**Model competence.** Last, deliberately. It is what people worry about first and it is the concern
that expires: it improves on its own, and the compiler catches most of what it gets wrong. The
structural limits above do not improve on their own.

## When not to generate at all

If nobody can review the output, generating it is worse than not having it, because it produces
artefacts that look reviewed.

If the vocabulary has operations nobody has thought hard about, fix that first. Generation
multiplies whatever the vocabulary permits.

And if the rules are stable, few, and already understood — generation is solving a problem you do
not have. The vocabulary is still worth building, for chapter 1's original reason. The generation
is optional.
