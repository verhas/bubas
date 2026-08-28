# Standing in for the world

<!-- abstract -->
Your rule calls operations that load claims, send messages and charge budgets, and a test cannot
have those really happen. Mocking as an idea: the rule under test stays real, everything it reaches
for is pretended. What that buys, and the one thing it can never tell you.
<!-- /abstract -->

---

## The problem, stated plainly

A rule is a small thing surrounded by a large world. `APPROVE` moves money. `ROUTE` reads the
approval policy out of some system. `ANOMALY_SCORE_OF` asks a model. Running a rule for real means
running all of that for real, which is not a test — it is a payment.

So a test replaces them. The rule itself stays exactly as it is, unmodified and uninstrumented, and
every operation it reaches for is stood in for by something the test controls.

Two words for the same idea: the operations are **mocked**, and the rule is the **subject**.

## What that gets you

**Determinism.** The rule asked what the claim came to and got 128.40, because the test said so.
Not because a database happened to hold that today.

**Cases you could not otherwise arrange.** A claim that breaches the budget of a cost centre that
does not exist. A model that returns 10. A claim with no lines at all. Every one of these is one
line in a test and a week of arranging in a real system.

**Speed, and no consequences.** Nobody is paid, nothing is written, nothing is sent.

**A record of what the rule did.** The framework remembers every call — which operations, in what
order, with what arguments — so a test can assert on things that leave no return value. `APPROVE`
answers nothing; the only evidence it ran is that it was called.

## What it can never tell you

One limitation, and it is not small, so it belongs here rather than in a footnote.

**A mocked operation tells you nothing about the real one.** If a test says `TOTAL_OF` returns
128.40 and the real `TOTAL_OF` has a rounding bug, every test in your suite passes and the rule is
wrong in production. Mocking tests the *rule*, on the assumption that the vocabulary does what its
description says.

That assumption is the seam. It is not a flaw in the approach — it is the same seam that lets a
subject-matter expert review the rule without reading Java, and you cannot have one without the
other. But it means a BUNIT suite is not the only testing your application needs. The operations
themselves are ordinary Java and need ordinary Java tests, which is Part 3's problem.

What you get is a clean division: BUNIT proves the policy is encoded correctly, and Java tests
prove the operations do what they claim. Neither substitutes for the other.

## Mocking is not optional here

In most codebases, mocking is a matter of taste. You can argue for a real database in tests, and
plenty of people do, with reason.

That argument does not survive contact with a BUBAS vocabulary, for a structural reason worth
seeing. Every value the domain owns is opaque — a `Report`, an `Item`, a `CostCentre`. A test
cannot construct one, because there is no syntax for constructing one and no way to reach inside if
there were. So a test cannot hand a rule a real claim. It never had that option.

What it can do is hand the rule something that *stands for* a claim, and mock every operation that
would have looked at it. That is the next chapter.

The consequence is worth stating: **an opaque-valued surface has to be mocked as a whole.** If
`ITEM_AT` is mocked and returns a stand-in, then `AMOUNT_OF` and `CATEGORY_OF` must be mocked too,
because the real ones expect a real line and will get something else. Leaving half of it real is the
commonest mistake in a first BUNIT test, and chapter 17 is about where the line honestly falls.

## What it looks like

Three shapes cover nearly everything.

**An operation that answers.** `"TOTAL_OF" WITH ARGS("the claim") RETURNS 128.40` — when asked
about that claim, say this. Without `WITH ARGS`, it answers the same for any call.

**An operation that does something.** `"APPROVE _" IS MOCKED` — let it be called, do nothing,
remember that it happened. There is nothing to return, so there is nothing to say.

**An operation that writes into a variable.** `"ROUTE _ TO _ AT _" SETS "approver" TO "..."` —
because a mocked command does not run its own handler, so whatever it would have written has to
come from somewhere. Chapter 18 is about the checking around that, which is stricter than you
expect and for good reason.

## The thing to keep hold of

A test describes a world and then asserts what the rule did in it. The mocks are the world. They
are not the thing being tested, and every line of them is an assumption you are making about the
vocabulary.

That is worth remembering when a test grows long. Twenty lines of mocks before a two-line assertion
usually means the rule reaches for too much, and the rule is what you should look at — not the test.
