# Defining types

<!-- abstract -->
Opaque types from the embedder's side; total opacity as a design choice.
<!-- /abstract -->

---

## A Java class becomes a domain value

<!--INCLUDE
from: "../../bubas-doc/src/test/java/javax0/bubas/doc/expense/Expense.java"
start: '// snippet: report-class'
end: '// end snippet'
prefix: "```java"
postfix: "```"
margin: 0
_content_generated_: 754:md5:83aea2551d757157be61a10d30fb40c3
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
    private final List<Item> items;

    Report(long id, String employee, List<Item> items) {
        this.id = id;
        this.employee = employee;
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

An ordinary class. It has fields, methods, a `toString`, and none of that is visible to BUBAS. It
implements no interface, extends nothing, and touches nothing from the BUBAS API.

The one concession is the description, which says what a claim is in the business rather than what
it holds. It is read when the vocabulary is exported for review — chapter 26 — and ignored the rest
of the time.

It becomes a type by being registered:

<!--INCLUDE
from: "../../bubas-doc/src/test/java/javax0/bubas/doc/expense/Expense.java"
start:
  pattern: '\.defineOpaqueType\("Report"'
  include: true
end:
  pattern: '\.defineFunction\("TOTAL_OF"'
  include: false
prefix: "```java"
postfix: "```"
margin: 0
_content_generated_: 55:md5:7a64656e2f2a19b8e0e4eb4c9b3208d1
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```java
.defineOpaqueType("Report", Report.class)
```
<!--/INCLUDE-->

The name is what programs write. The Java class is what handlers receive. Nothing connects them
except this line, which means the same class can be exposed under different names in different
languages, and a language can be built over classes you do not own.

## The name is a reserved word

Registering `Report` reserves it — in every casing. `report`, `REPORT` and `rEpOrT` are all taken,
which is why every program in this book calls its variable `claim`.

That surprises people, so it is worth deciding names with it in mind. A type named after the thing
its variables will want to be called is a type whose users have to think of a synonym every time.
`Report` and `claim` work because they are different words for the same idea; `Claim` and `claim`
would not.

The diagnostic says what happened rather than merely reporting a reserved word, which helps, but not
as much as picking the name well.

## Opacity is total, and that is the design

A registered type is a black box. Programs cannot read fields, call methods, render one as text,
compare two, or construct one. Chapter 5 covered what that feels like from inside a rule. Here is
why you should want it.

**The vocabulary stays a list somebody wrote.** If rules could read fields, then what a rule can
know would be decided by the shape of a Java class — and classes grow. Somebody adds a field for an
unrelated feature and the vocabulary has silently gained a word nobody decided on, documented, or
reviewed. Chapter 11's document would stop being a complete account.

**Your domain model stays yours.** Because `Report` is opaque, you can rename its fields, change its
representation, or replace it with a different class entirely, and no rule breaks. The only contract
is the operations, which is a contract you wrote deliberately. Partial exposure would make every
field a public API you did not know you had published.

**Tests become possible.** Chapter 15's tokens work precisely because a rule cannot look inside. A
partially transparent value could not be stood in for by a name.

That third one is worth dwelling on. Total opacity is not one design decision; it is what makes the
testing story in Part 2 available at all. A vocabulary that exposed a claim's total as a readable
field would have saved one operation and cost the whole of BUNIT.

## Describing it, and the one case that cannot

The description goes on the class, and `defineOpaqueType` takes it from there. Nothing else is
needed, and nothing else is registered.

That is the ordinary case because it is the common one: a class your own team wrote, for a domain
your own team models, exposed to a language your own team built. Requiring anything more of it
would make the common case pay for the rare one.

The rare one is real, though. Some types cannot carry the annotation:

- a class from a library — a `java.time.LocalDate`, a `BigDecimal` wrapper somebody else ships
- a domain model shared with consumers that must not depend on BUBAS, where adding the annotation
  would make your model depend on one of the things that reads it
- a generated class, where the next generation would drop it

For those, describe the type on an empty interface carrying `@BubasDescribes(TheClass.class)`
alongside its `@BubasDescription`, and register it with `defineOpaqueTypeVia`. The interface exists
only to hold the words. It costs one file, and it is the escape route rather than the road.

Both forms produce the same vocabulary entry. A reader of chapter 11's document cannot tell which
was used, and should not be able to.

## When not to make something opaque

Not every value from your domain needs to be a type.

If a thing is genuinely a number, a piece of text, or a yes-or-no, expose it as one. A claim's
reference number is a `STRING`; making it an opaque `ClaimReference` buys nothing and costs every
rule the ability to put it in a message.

The test is whether rules need to *do* anything with the value beyond passing it along. If they only
ever hand it to other operations, opaque is right. If they need to compare it, print it, or build
a message out of it, it is a value and should be one.

The middle case — a thing rules mostly pass along but occasionally need to name — is handled by an
operation, not by making the type transparent. `MERCHANT_OF(line)` returns a `STRING` precisely so
that `Item` need not be readable.

## What is coming

Types are the nouns. The next two chapters are the verbs: functions that answer questions, and the
pattern language that turns a class into a statement.
