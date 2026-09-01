# Describing and exporting

<!-- abstract -->
Writing the vocabulary document that chapter 11 taught people to read: descriptions, where they
live so that domain classes stay clean, the export itself, and a checksum that tells you when a
reviewed vocabulary has changed shape since anybody looked at it.
<!-- /abstract -->

---

## The document is generated, so it cannot lie about what exists

Chapter 11 showed a reader how to find out what a language can do. This is where that document
comes from:

<!--INCLUDE
from: "../../bubas-doc/src/test/java/javax0/bubas/doc/expense/VocabularyTest.java"
start:
  pattern: '// snippet: export'
  include: false
end:
  pattern: '// end snippet'
  include: false
prefix: "```java"
postfix: "```"
margin: 0
_content_generated_: 113:md5:06f14b8b8e6382eb50a93c26d175798a
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```java
final var export = VocabularyExport.of(stage.getValue());
final var document = export.asMarkdown();
```
<!--/INCLUDE-->

It is built from the sealed language, so it cannot describe an operation that does not exist and
cannot omit one that does. There is no wiki to keep up to date and no comment block to drift.

`asJson()` gives the same content in a form a tool can read, which is what you want when the
consumer is a generator rather than a person — chapter 32's subject.

## It refuses to be built out of nothing

`VocabularyExport.of` throws if anything is undescribed, listing everything that is:

<!--INCLUDE
from: "../../bubas-doc/target/doc-outputs/export-refusal.txt"
prefix: "```"
postfix: "```"
_content_generated_: 198:md5:030fd57327bf1e6f4addfc0f6fbb929d
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```
nothing describes:
        opaque type Ledger
    An export says what a vocabulary means, so it cannot be built out of things nobody has said anything about. Add @BubasDescription to each.
```
<!--/INCLUDE-->

That is a deliberate refusal rather than a nicety. A document with holes in it is worse than no
document, because a reader who finds three operations undescribed stops trusting the other twenty.

The practical consequence: put the export in your build. A vocabulary that cannot describe itself
fails, and the moment it fails is the commit that added the operation.

## Where descriptions live

<!--INCLUDE
from: "../../bubas-doc/src/test/java/javax0/bubas/doc/expense/Expense.java"
start: '// snippet: report-class'
end: '// end snippet'
prefix: "```java"
postfix: "```"
margin: 0
_content_generated_: 850:md5:698bee3d7097515eb522590fabee6284
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```java
/** A value BUBAS holds and passes but cannot look inside. */
@BubasDescription("""
        One employee's expense claim for a trip or a period.
        A program is given one to decide about; ask TOTAL_OF what it comes to.
        """)
public static final class Report {
    final long id;
    private final String employee;
    private final LocalDate submitted;
    private final List<Item> items;

    Report(long id, String employee, LocalDate submitted, List<Item> items) {
        this.id = id;
        this.employee = employee;
        this.submitted = submitted;
        this.items = items;
    }

    BigDecimal total() {
        return items.stream().map(Item::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    public String toString() {
        return "report " + id + " (" + employee + ")";
    }
}
```
<!--/INCLUDE-->

For a **function or statement**, the annotation goes on the handler class, which you own and which
exists only for this purpose.

For a **type**, it goes on the class itself, as above, and `defineOpaqueType` takes it from there.

The exception is a type whose class cannot carry the annotation — one from a library, or a domain
model shared with consumers that must not depend on BUBAS. Describe those on an empty interface
carrying `@BubasDescribes(TheClass.class)` and register with `defineOpaqueTypeVia`. Chapter 22 has
the argument for when that is worth doing; the export cannot tell the difference either way.

## Writing them

The audience is somebody who will never see the Java, and that changes what a good description
says.

**Say what it is in the business, not what it holds.** Look again at `Report` above: one employee's
claim for a trip or a period, and which operation to ask about it. Not a list of fields — a reader
cannot reach them anyway.

**Point at the neighbouring operations.** `Item`'s description names `AMOUNT_OF`, `CATEGORY_OF`,
`MERCHANT_OF` and `HAS_RECEIPT`, so a reader who has just met a line knows what to ask it. In a
document read by someone assembling a rule, those signposts do more than prose does.

**Say what is decided elsewhere.** The anomaly score's description ends *"It is an opinion, not a
decision: the rule decides what score is too high."* That sentence is doing chapter 25's work in the
place where a rule-writer will actually meet it.

**Do not describe the implementation.** Whether a total is cached, which service is called, how a
score is computed — none of it can be acted on by the reader, and all of it will be wrong
eventually.

**Write in the second person or not at all.** These are instructions to somebody using the
vocabulary, not notes to yourself.

## Knowing when a vocabulary has moved

A description is prose, and prose goes stale silently. There is a mechanism for the part of it that
can be checked mechanically.

`Surface.checksum(Class)` hashes a class's public surface — its methods and their shapes. Recording
it with `@BubasReviewed` says *somebody read this and agreed with what it said, at this shape*. When
the shape changes, the recorded checksum no longer matches, and the export tells you which
descriptions need re-reading.

What it catches: an operation gaining a parameter, changing its return type, or acquiring a new
public method. Real changes that plausibly invalidate a sentence somebody wrote.

What it cannot catch, and this is the important half:

- **A behaviour change behind an unchanged signature.** The commonest way for a description to
  become false, and the checksum is blind to it by construction.
- **A model swapped out behind the same operation.** Chapter 25's warning, restated.
- **A description that was never accurate.**

So the checksum is a prompt for a human review, not a substitute for one. Treated as the latter it
is worse than nothing, because it produces the feeling of having a process.

An annotation that is absent is ignored rather than treated as a failure. That is deliberate: a
vocabulary should be usable before anybody has reviewed it, and adopting the review discipline
should be a choice you make when you are ready to keep it.

## Who should read it, and when

The export is not documentation you write and file. It is the artefact chapter 1's finance manager
reviews **before any rules exist**.

That is the moment it pays for itself. A vocabulary reviewed at the start is a set of operations
somebody with domain authority agreed the domain has. Reviewed after fifty rules depend on it, the
review can only ratify.

Three moments worth building a habit around: when the vocabulary is first designed, when an
operation is added, and when the checksum says a shape moved. The first is the one people skip and
the one that matters most.

## What is coming

The vocabulary is defined, described and reviewable. The next chapter is the last piece of building
it: teaching BUNIT about statements of your own, so that the tests in Part 2 work for operations
this book never saw.
