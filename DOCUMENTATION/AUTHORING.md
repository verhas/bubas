# Authoring BUBAS Documentation

**Status** Stage 1 built; the five-minute tutorial is live · **Applies to** the tutorials and the book, not to [`README.md`](../README.md) or [`SPEC.md`](../SPEC.md)

This document records how BUBAS tutorials and the book are built, and why. It exists because
documentation rots: examples drift from the code they claim to describe, quoted output stops
matching what the program prints, and nothing notices until a reader does. Every decision below
is aimed at making that class of failure a build failure.

It is a decision record, not a style guide. It says what was chosen, what was rejected, and what
would have to change for a decision to be revisited.

---

**Contents**

<!--TOC min-level: 2
max-level: 2
_content_generated_: 187:md5:39f3729107f728d036d2fd4946c72eb1
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
- [The documents](#the-documents)
- [Decisions](#decisions)
- [Mechanics](#mechanics)
- [Rejected alternatives](#rejected-alternatives)
- [Order of work](#order-of-work)
- [Open](#open)
<!--/TOC-->

---

## The documents

Three, sharing one worked application:

| Document           | Reaches         | Purpose                                              |
|--------------------|-----------------|------------------------------------------------------|
| 5-minute tutorial  | stage 1–2       | One program, one vocabulary, the thesis made visible |
| 15-minute tutorial | stage 1–5       | Enough language to write a real rule                 |
| The book           | the full ladder | Everything, chapter by chapter                       |

A reader of the 5-minute tutorial does not meet a cut-down version of the book's application. They
meet its **first two stages**. The application is the same one throughout; short documents simply
stop earlier.

## Decisions

### D1 — One application across all three documents

The reader learns the domain once. By the middle of the book they spend their attention on the
language rather than on re-orienting to a new example every chapter.

The cost is chapter independence: a reader who lands on chapter 14 needs to know what the language
knows by then. That is paid for by D6 — each chapter opens with a generated vocabulary listing, so
landing in the middle is always possible.

### D2 — Stages are additive, and stages are views rather than copies

Each stage **adds** to the previous one. It does not rewrite it.

This is a constraint on the writing, and it is the decision that makes the whole problem go away:
if nothing is ever rewritten, there is exactly one copy of every line, and a fix at stage 1 reaches
stage 40 because there is nowhere else for it to be. Propagation is not solved, it is *absent*.

Stages are therefore selections from one final, compiled codebase — named regions pulled out by
`INCLUDE` — never separate directories holding separate copies.

This applies to the **vocabulary** as much as to the programs, and that is not optional: adding
stage 2's `ESCALATE` to a single shared language object would have silently falsified the
five-minute tutorial's claim to show a language of three operations. Each stage is instead a
builder method calling the one before it — `core()`, then `escalating()`, then `itemised()` — so
every document sees the definitions its stage actually had. A test compiles a later stage's program
against an earlier stage's language and requires it to fail, which is what stops the staging from
quietly becoming decorative.

### D3 — The domain is personal expense report approval

BUBAS claims that the person who owns a rule can read and verify the program implementing it. A
reader can only feel that claim if they can play the expert while reading.

Almost nobody can audit a B2B order-approval rule. Everybody can audit an expense rule — they know
a €340 dinner for one needs a receipt, that the VP signs above some threshold, that per-diem and
actuals are different things. The domain lets the book ask "is this rule right?" and get a real
answer from the reader.

It also carries a book without contrivance: totals, limits, per-category limits, receipts,
escalation, foreign currency, policy exceptions, notification, testing, export. Ten chapters where
each one adds.

### D4 — README, SPEC and the published article keep order approval

They are not being converted. The article is published; the other two are correct as they stand.

This is deliberate rather than merely cheap. A language whose entire pitch is *you define the
vocabulary* looks weaker when every document uses the same twelve words — readers start reading
`LOAD_ORDER` as a built-in. Two unrelated vocabularies across the documentation set demonstrate
that neither one is the language.

The BUNIT corpus (71 files) also keeps the order domain. It tests dispatch interception and mock
consistency; no reader opens it, and rewriting it would improve nothing anyone sees.

### D5 — Every fragment comes from a compiled, tested source

No fragment is ever typed into a document. Java and `.bu` sources live in the `bubas-doc` module,
are compiled by the reactor, and are exercised by tests. Documents pull named regions out of them.

Markers are host-language comments, so `INCLUDE` excludes them from the rendered output:

```
// snippet: approve          (Java)
' snippet: approve           (BUBAS)
```

Selection is by **named marker, never by line range**. Line ranges break the moment someone inserts
a line, and break silently — the document still renders, showing the wrong code.

### D6 — Quoted output is derived, not duplicated

A test that asserts `"3 reports approved"` while the document also contains `"3 reports approved"`
has two copies agreeing by luck, checked by nothing.

Instead the test **writes the real output** to `target/doc-outputs/<name>.txt`, and the document
includes that file. One copy, derived. Every number, printed line, and quoted diagnostic in the
prose is something the code actually produced during the build.

The same mechanism generates each chapter's vocabulary reference: `VocabularyExport.asMarkdown()`
run against that stage's language object produces the listing, so "what the language knows at this
point" cannot drift from what it actually knows.

### D7 — A stale document fails the build

```
mvn -B verify && mdship update DOCUMENTATION/**/*.md && git diff --exit-code
```

If a committed document no longer matches what the code produces, CI fails. This is the decision
that makes the rest enforceable; without it every mechanism above is optional, and optional
anti-rot machinery rots.

### D8 — Where a stage must rewrite, both versions are real and named

Sometimes the rewrite *is* the lesson — here is the obvious approach, here is why it fails, here is
the fix. D2 cannot express that, and pretending otherwise produces a worse tutorial.

For those cases keep two real, differently named, separately compiled and tested things
(`NaiveApproval` and `Approval`). The duplication is two adjacent copies that the build exercises,
not N copies across N directories that nothing compares.

Duplication is permitted when it is the point, and never when it is a side effect of layout.

### D9 — Asides are allowed; contorting the domain is not

Some features do not belong to expense approval. When one does not fit, write a small standalone
example, mark it plainly as an aside, and keep it in the same module under the same rules.

Inventing `SUM_EXPENSES_VARIADIC` purely to demonstrate varargs is worse than a two-line aside, and
readers can tell.

### D10 — The AI operation returns a score, never a verdict

The book includes an operation backed (nominally) by an LLM: an anomaly score for a line item.

```
ANOMALY_SCORE_OF(item Expense) -> INTEGER      -- 1..10
```

Not `SHOULD_APPROVE(report) -> BOOLEAN`. The difference decides **who owns the policy**. With a
score, the rule stays in the program where the expert can read and argue with it:

```
IF ANOMALY_SCORE_OF(item) >= 8 THEN
    ESCALATE item, "flagged for review"
END IF
```

That `8` is visible, auditable, and changeable by the person accountable for it. Return a boolean
and the model owns the decision while BUBAS is reduced to glue.

Generalised: **advisory outputs — scores, classifications, extractions — keep the decision with the
expert; verdict outputs move it to the model.**

It takes a line item rather than a whole report, so the program iterates and the "do not call this
in a tight loop" lesson has a real cost behind it.

It is also the natural motivation for BUNIT. Mocking a database is advice nobody believes, because
you can just run a database. A non-deterministic operation cannot be tested around at all, so
mocking stops being hygiene and becomes the only way forward.

The chapter states plainly that this is the one operation whose behaviour can change without any
review checksum changing. The vocabulary bounds what can be *named*, not what a named thing *does*.

### D11 — No network and no API key in any build

The AI operation ships as a deterministic mock keyed on amount, category and merchant, returning
plausible varied scores — not a constant, or the examples read as fake.

A real HTTP-calling implementation appears in the text as illustration and is never wired into the
reactor. The build must run offline, for anyone, forever.

### D15 — Staged programs are real copies, and compilation is what makes that acceptable

For the vocabulary, D2 holds exactly: there is one definition of `TOTAL_OF` and one of `APPROVE`.

The programs are different. `escalating-expense.bu` restates lines from `approve-expense.bu`, and
`itemised-expense.bu` restates lines from both, because a `.bu` file has to be a whole program.
There is no way to show a rule growing without showing it more than once, and showing it grow is
the point of a tutorial.

What makes the copies safe is that **every stage is compiled and run**. Rename an operation, change
its arity, change a parameter type, and every stage that mentions it fails the build. For
everything the compiler can see, compilation is the propagation mechanism D2 wanted.

What it does not catch: a policy number changed in one stage and not another. Both still compile.
The generated transcripts narrow the gap — a changed threshold changes that stage's output, and the
staleness gate then fires — but two stages can still disagree about what the meal cap is with
nothing going red, as long as each is internally consistent. **When a constant appears in more than
one stage, the other stages have to be checked by hand.** This is the one place in the design where
a human still carries the propagation, and it is worth keeping small: prefer thresholds that appear
in exactly one stage.

### D13 — Operations are named to read as English, not as stacked nouns

`TOTAL_OF(claim)` over `REPORT_TOTAL(claim)`. `LOAD` over `LOAD_REPORT` while nothing else is
loaded. The reader of a BUBAS program is not a programmer scanning an API listing; they are reading
a sentence, and three nouns in a row is not a sentence.

Qualify a name only when something else in the vocabulary would otherwise be confusable with it. A
name that spells out what its own argument already says is redundant twice over: the argument is
right there.

### D14 — The host passes opaque values in; the language does not fetch them

Where the application already holds the value a rule operates on, it passes it as a program
argument. Opaque values cross the boundary as arguments perfectly well, so an operation that
fetches by identifier is a round trip through the language for nothing.

It also invents a failure the rule then has to handle: a fetch can miss, and a stage with no way to
express "not found" either ignores that or grows machinery it did not want. Passing the value in
removes the case rather than handling it.

Fetching operations are legitimate where a program genuinely works on things it was not handed —
iterating a queue of pending claims, looking up the approver for an escalation. That is a later
chapter, and by then the language can say something sensible about a miss.

### D12 — Keep the AI material proportionate

One operation among twenty. If the AI chapter grows into an AI book, the thesis blurs and the
documentation stops being about BUBAS.

## Mechanics

```
bubas-doc/                                  in the reactor, excluded from deployment
  src/test/java/…/doc/expense/Expense.java  the vocabulary, snippet-marked
  src/test/java/…/doc/expense/*Test.java    compiles and runs every program, asserts the
                                            outcome, and writes what it printed to
                                            target/doc-outputs/
  src/test/resources/programs/*.bu          the programs the documents show
DOCUMENTATION/
  TUTORIAL/five-minutes.md
  TUTORIAL/fifteen-minutes.md
  BOOK/01-….md                              one file per chapter
```

Everything lives in **test scope**, following `bubas-test`, which already does exactly this: a
vocabulary and a corpus of programs that exist to be exercised rather than published. Test scope
keeps the module out of the published artifacts without a second mechanism, and avoids a
`module-info.java` whose only purpose would be to satisfy a build the reader never sees.
`maven.deploy.skip` makes the exclusion explicit anyway.

The gate is only known to work if it has been seen to fail. When changing how documents are
generated, deliberately alter a program, regenerate, and confirm `git diff --exit-code` goes red
before trusting it again.

## Rejected alternatives

**A directory per stage.** The obvious approach, and the one this design exists to avoid. Each
stage is a real compilable copy, which is its appeal; a fix at stage 1 must then be hand-carried to
every later stage, and nothing detects the case where it was not. Across a book that becomes dozens
of near-identical trees whose divergence is invisible until a reader hits it.

**Stages as commits, propagated by `git rebase`.** Rebase genuinely is the missing
change-propagation-through-history tool, and conflicts surface exactly where a change matters.
Rejected because the working tree holds only one stage at a time: the doc build would have to
materialise worktrees before including anything, and ordinary maintenance turns into
checkout-edit-rebase. Worth reconsidering only if the additive constraint (D2) proves unsustainable.

**Stages as a patch series.** Same propagation benefit, without the worktree problem, but patches
are unreadable as a maintenance surface and conflict resolution is worse than the disease.

**Separate examples per document.** Lower entry cost for short documents, and better chapter
independence. Both objections are answered by D2 plus D6 — short documents stop at early stages,
and generated vocabulary listings let readers land mid-book — so the continuity of one application
wins.

**Hand-written vocabulary tables per chapter.** Rejected in favour of generating them from the
stage's language object (D6). A hand-written table is exactly the kind of thing that is correct on
the day it is written.

## Order of work

1. The 5-minute tutorial, end to end — module, markers, output derivation, CI gate — on the
   smallest content available. A wrong pipeline is then discovered after one page rather than after
   ten chapters.
2. The 15-minute tutorial: receipts, per-category limits, escalation.
3. The book's chapter ladder.

## Open

- The AI operation's name: `ANOMALY_SCORE_OF` versus `FISHINESS_OF`. Undecided, and not needed
  until the book reaches it. Stage 1's names are settled under [D13](#d13-operations-are-named-to-read-as-english-not-as-stacked-nouns).
- Whether README and SPEC should eventually pull their examples from a module too. They are
  hand-maintained copies today (D4 keeps their domain, not their staleness), and the argument for
  fixing that is the same argument this document makes.
