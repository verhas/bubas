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
carries no BUBAS annotations, implements no interface, extends nothing.

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

## Describing it

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

The description lives on a separate empty interface rather than on `Report` itself, and
`defineOpaqueTypeVia` takes that interface instead of the class.

Two reasons. A domain class is usually shared — `Report` may belong to a module that knows nothing
about BUBAS — and annotating it would make your domain model depend on one of its consumers.
Describing it at each registration instead would copy the same prose once per language.

An empty interface is neither. It exists only to carry the annotation, it sits beside the language
that needs it, and it costs one file.

Note what the description says: what a claim *is* in the business, and which operations answer
questions about it. Not what it contains. Chapter 26 is about writing these well; the shape to aim
for is visible here.

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
