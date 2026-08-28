# Knowing what you can say

<!-- abstract -->
Every BUBAS language can describe itself: a document listing every operation, what it answers, and
what it is for, written for you rather than for a programmer. How to read it, how to tell whether a
rule you have been asked to write is expressible, and how to ask for an operation that does not
exist yet. That last one is a normal request, and Part 3 is where it gets answered.
<!-- /abstract -->

---

## The question

You have been handed a language and asked to write a rule in it. Before you can start, you need to
know what it can say.

In a general-purpose language that question has no good answer — the honest reply is "almost
anything, go and read the codebase." In BUBAS it has an exact one, because the set of words a
program can use is finite and somebody wrote it down. Every sealed language can produce a document
listing all of it.

That document is not generated from comments, or from a wiki somebody keeps up to date. It comes
from the language itself, so it cannot describe an operation that does not exist and cannot omit
one that does.

## Values

It opens with the things your rules will hold but never open:

<!--INCLUDE
from: "../../bubas-doc/target/doc-outputs/vocabulary-stage-5.md"
start:
  pattern: '^## Values'
  include: true
end:
  pattern: '^## Functions'
  include: false
_content_generated_: 428:md5:915ad576e0cd504cc7adf56060b19f6e
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
## Values

A script may hold one of these, pass it and store it in an array. It can never look inside: every question about one is a function.

### Report

One employee's expense claim for a trip or a period.
A program is given one to decide about; ask TOTAL_OF what it comes to.

### Item

A single line on a claim: what was bought, from whom, for how much.
Ask AMOUNT_OF, CATEGORY_OF, MERCHANT_OF and HAS_RECEIPT about one.

<!--/INCLUDE-->

Notice what the descriptions do. They do not say what a `Report` is made of, because you cannot
reach into one. They tell you what it *is* in the business, and which operations to ask about it.
That is the right shape for a reader who will never see the Java, and it is the shape to insist on
when you review a vocabulary somebody has drafted for you.

## Questions you can ask

Then the functions — everything the language can find out:

<!--INCLUDE
from: "../../bubas-doc/target/doc-outputs/vocabulary-stage-5.md"
start:
  pattern: '^### TOTAL_FOR'
  include: true
end:
  pattern: '^### ANOMALY_SCORE_OF'
  include: false
_content_generated_: 119:md5:c291759219a471a507d53169a889c83e
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
### TOTAL_FOR(claim Report, category STRING) -> DECIMAL

What one category of spending on a claim comes to, in euro.

<!--/INCLUDE-->

The heading is the whole signature: the name, what it needs and of what type, and what it answers
after the arrow. `TOTAL_FOR(claim Report, category STRING) -> DECIMAL` tells you it wants a claim
and some text and gives back a decimal, which is everything you need to use it correctly.

An entry with no arrow answers nothing and is written as a statement rather than used in an
expression — which is chapter 4's distinction, visible in the document.

## Things you can tell it to do

Then the statements:

<!--INCLUDE
from: "../../bubas-doc/target/doc-outputs/vocabulary-stage-5.md"
start:
  pattern: '^### REJECT'
  include: true
end:
  pattern: '^### ESCALATE'
  include: false
_content_generated_: 155:md5:7139e0cb24ef10b7a9a2d6ea6702429c
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
### REJECT _, _

```
REJECT {expression/Report:claim}, {expression/STRING:reason}
```

Refuses a claim, recording the reason the claimant will be shown.

<!--/INCLUDE-->

The heading shows the shape with the blanks marked, and the block underneath spells it out
precisely. That block is written for the person who built the language, and you can mostly skim it,
but two parts of it are worth learning to read.

`{expression/Report:claim}` means *a claim goes here, and it must be a `Report`*. Anything of that
type will do — a variable, or something a function just answered.

Some entries end with a line like `Leaves a value in: name`. That is the document telling you that
the statement *writes* to the variable in that position rather than only reading it. `DECLARE`
does; `REJECT` does not. It matters when you are working out whether a variable has been given a
value yet, which is the rule chapter 8 spent its first section on.

You will also notice that `DECLARE` and `=` are in this list, alongside `APPROVE` and `REJECT`.
That is chapter 4's point made concrete: they are operations somebody installed, not syntax the
language was born with.

## What the document does not tell you

Two limits, and both matter.

**It describes what an operation is for, not what it does.** The description is prose somebody
wrote. Behind `ANOMALY_SCORE_OF` there is code, and the code could do more than the sentence admits.
For most vocabularies, written by your own colleagues for your own application, that gap is
uninteresting. It is worth remembering it exists.

**It cannot tell you whether the descriptions are current.** An operation's behaviour can change
without its description changing. There is a way to record that a vocabulary was reviewed at a
particular shape, so that adding or removing an operation shows up as a review that needs redoing —
chapter 26 covers it — but nothing detects a description that has quietly stopped being true. That
is a job for the people, not the tooling.

## Can I write the rule I have been asked to write?

This is the practical use of the document, and it is worth doing before writing anything.

Take the policy you have been handed and go through it clause by clause, asking of each: *which
operation answers this?* Most clauses will map onto something. The ones that do not are what you
have learned.

Three ways it can go.

**The question exists under another name.** You want "how much was spent on food" and the
vocabulary offers `TOTAL_FOR(claim, "meals")`. Fine.

**The question can be built from others.** You want the average per line, and there is
`TOTAL_OF` and `ITEM_COUNT`. Also fine, if the arithmetic is genuinely part of the rule — but see
the warning below.

**The question is not there at all.** You want to know whether the claimant is a contractor, and
nothing in the vocabulary knows anything about people. Nothing you can write will get you there,
because there is no way in. Stop, and ask.

## Ask, rather than working around

When a question is missing, there is nearly always a way to fake it. You can compare a merchant name
against a list of strings you type into the rule. You can infer a trip's length from the number of
lodging lines. You can decide that anything over some amount is probably a contractor.

Do not.

Every one of those moves a piece of domain knowledge out of the vocabulary — where it is written
once, named, reviewed, and shared by every rule — into one rule, where the next person to need it
will not find it and will invent their own slightly different version. Two rules that disagree
about what a contractor is, in a way nobody notices for a year, is a worse outcome than waiting a
week for an operation.

The instinct to work around it is strong, because working around it feels like getting on with the
job. It is worth resisting precisely as strongly.

## What makes a good request

The person who will build the operation is doing Part 3's work, and the request that helps them most
is not "please add `IS_CONTRACTOR`."

Tell them the **policy clause** you are trying to express, in the words it was given to you. Tell
them **what you would do with the answer** — branch on it, put it in a message, compare it against a
threshold. Tell them **what kind of answer** you expect: a yes or no, a number, one of a few known
words.

That is enough for them to decide the shape, and the shape is the part that is hard to change later.
A question exposed as a boolean when it should have been a score is chapter 10's mistake, and it is
much easier to avoid before the operation exists than after twenty rules depend on it.

## End of Part 1

You can now read a BUBAS program, write one, predict what the compiler will refuse, find out what a
language you have been given can do, and ask for what it cannot.

What you cannot yet do is show that a rule is *right*. Reading a rule and agreeing with it is
worth a great deal, and it is not the same as demonstrating that it does what you think on the
claims it will actually meet — including the awkward ones nobody thinks of until they arrive.

Part 2 is about that, and it is written in the same language, for the same reader.
