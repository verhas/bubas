# Exposing a model

<!-- abstract -->
Putting an LLM behind an operation, and the design rule that decides whether it was worth doing:
return a score, never a verdict. Advisory outputs leave the decision with the expert; verdict
outputs move it to the model and reduce your language to glue. And the limit worth stating plainly:
this is the one operation whose behaviour can change without anybody editing a line of code.
<!-- /abstract -->

---

## An operation like any other

Putting an LLM behind an operation changes nothing about how the language sees it. It is a function;
it takes an argument; it answers a value. Everything that is different about it — that it costs
money, that it takes a second, that it will not say the same thing twice — is behind the boundary,
where the rule cannot see it and does not need to.

What that leaves you is one decision, made once, when you write the signature. It is the subject of
this chapter, and it decides whether the arrangement was worth building.

Here is the operation this book uses:

<!--INCLUDE
from: "../../bubas-doc/src/test/java/javax0/bubas/doc/expense/Expense.java"
start: '// snippet: anomaly-score'
end: '// end snippet'
prefix: "```java"
postfix: "```"
margin: 0
_content_generated_: 1456:md5:7051395dc29d601ef0cb69ffebbcaa09
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```java
/**
 * Stands in for a model. A real implementation would send the line to a service and return
 * what it answered; this one is a fixed rule of thumb, because the build must run offline for
 * anyone, forever, and a book whose examples change between printings is no use.
 * <p>
 * It returns a <em>score</em>, never a verdict. What counts as too high is a threshold in the
 * rule, where the person accountable for the policy can read and change it. See
 * {@code DOCUMENTATION/AUTHORING.md} D10.
 */
@BubasDescription("""
        How unusual a line of spending looks, from 1 (ordinary) to 10 (very unusual).
        It is an opinion, not a decision: the rule decides what score is too high.
        """)
public static final class AnomalyScoreOf {
    private static final BigDecimal LARGE = new BigDecimal("100");
    private static final BigDecimal VERY_LARGE = new BigDecimal("500");
    private static final BigDecimal LAVISH_MEAL = new BigDecimal("75");

    public long call(Context ctx, Item line) {
        var score = 1L;
        if (line.amount().compareTo(LARGE) > 0) {
            score += 2;
        }
        if (line.amount().compareTo(VERY_LARGE) > 0) {
            score += 3;
        }
        if (!line.hasReceipt()) {
            score += 2;
        }
        if ("meals".equals(line.category()) && line.amount().compareTo(LAVISH_MEAL) > 0) {
            score += 3;
        }
        return Math.min(score, 10);
    }
}
```
<!--/INCLUDE-->

That implementation is a fixed rule of thumb, because a book's examples must run offline and give
the same answer in every printing. A real one would send the line to an AI service and return what
came back. The shape is what matters, and the shape is the subject of this chapter.

Registering it is unremarkable — `.defineFunction("ANOMALY_SCORE_OF", AnomalyScoreOf.class)` — and
that unremarkableness is the point. From the language's side there is nothing special about an
operation backed by a model. Everything that is different about it lives behind the boundary.

## The rule that decides whether it was worth doing

It returns an `INTEGER` from 1 to 10. It could have returned a `BOOLEAN`.

`SHOULD_ESCALATE(claim) -> BOOLEAN` would work, would be less code in every rule, and would be a
serious mistake.

**With a score, the policy stays in the rule.** `IF worst >= flagAt THEN` is a line a finance
manager reads, disagrees with, and changes. When an auditor asks why this claim was flagged and that
one was not, the answer is a threshold somebody chose, in an artefact they can be shown.

**With a verdict, the policy moves behind the operation.** Not into the dark — it is in the Java,
under version control like everything else, and an engineer can read it, review it and explain it.
The difference is who. The person who owns expense policy cannot read Java, and they are the one the
auditor will ask and the one accountable for the answer. Every question about the rule now needs an
engineer to translate it, and the BUBAS program that existed so that expert could own the rule has
been reduced to glue between a service and a database.

Stated generally, and worth keeping when somebody asks you to add an operation:

> **Advisory outputs — scores, classifications, extractions — leave the decision with the expert.
> Verdict outputs move it to the model.**

This is not an argument against models. It is an argument about which side of the boundary the
decision sits on.

## Recognising a verdict in disguise

A boolean return is the obvious case. Two subtler ones are worth watching for.

**A score with only two values.** An operation returning 1 or 10 and nothing between is a boolean
that has learned to count. If the model genuinely only distinguishes two states, say so and accept
that the decision has moved.

**An operation named for the decision.** `RISK_OF(claim) -> STRING` returning `"HIGH"` or `"LOW"` is
a classification, which is advisory — but if the rule's only possible response is to escalate
anything `"HIGH"`, the threshold has been chosen by whoever wrote the classifier. Ask what the rule
would do with a third value. If there is no sensible answer, the categories are the policy.

The test that cuts through both: **could a person move the line without retraining anything?** With
a score and a threshold, yes. That is the property you are buying.

## Practical consequences to design for

**Calls cost time and money.** A rule may call the operation once per line, and chapter 10 pointed
out that the obvious way to write the loop calls it twice per line. You cannot fix that from here —
the rule is not yours — but you can make it cheap to get right by keeping the operation's cost
visible in its description, and you can cache inside the handler where the domain permits.

**Failure needs a decision, and it is yours.** A service can be slow, down, or answer nonsense. The
rule has no vocabulary for any of that. So the handler decides: throw, and the run fails with a
diagnostic; or return a conservative value, and the rule proceeds on an assumption nobody wrote
down. Neither is obviously right, and choosing silently is what makes it wrong. Chapter 30 is about
who sees the consequences.

**Determinism is gone, and it propagates.** Every rule calling this operation becomes untestable by
execution, which is chapter 19's subject and a real cost to the people writing rules. It is worth
paying once, for an operation that earns it, and not twice.

## The honest part

The vocabulary bounds what a program can **name**. It does not bound what a named thing **does**.

That has been true of every operation in this book, and it is worth repeating here because this is
where the gap is the widest. An operation described as returning an opinion about a line of spending
could, behind that description, send the whole claim to a third party, retain it, log the claimant's
name somewhere it should not be, or cost a euro a call. The description is what somebody wrote; the
behaviour is what somebody built.

Worse, it is the one operation whose behaviour can change **without anybody editing a line of
code**. A model is replaced, a prompt is tuned, a provider changes a default — and every rule
depending on it now decides differently. The review checksum of the next chapter will not notice,
because the vocabulary's shape has not changed. Nothing in BUBAS will notice.

So the discipline has to come from outside the language:

- **Pin the model version** in the handler, and treat changing it as a code change with a review.
- **Record what was asked and what came back**, alongside the decision, so an outcome can be
  explained six months later.
- **Test the boundaries**, as chapter 19 describes, so that a shift in behaviour has some chance of
  showing up as a failing case rather than as drift nobody sees.

None of that is BUBAS's job. All of it is yours, and it is the price of the one operation in the
vocabulary that has an opinion.

## What is coming

Every operation in this book carries a description written for somebody who will never see the
Java. The next chapter is about writing those, exporting them, and knowing when a vocabulary has
changed shape since anybody last looked at it.
