# BUBAS in Five Minutes

BUBAS is an orchestration language for people who own business rules and do not write Java. It has
no vocabulary of its own: everything a program can name is something you decided to expose. This
page shows one small language, one program written against it, and what happens when the program is
wrong.

Every fragment below is pulled from code the build compiles and runs. Nothing here was typed by
hand (not even by virtual LLM hands).pho
t

---

## A program you can already read

This is a complete BUBAS program in a language for approving expense claims:

<!--INCLUDE
from: "../../bubas-doc/src/test/resources/programs/approve-expense.bu"
prefix: "```"
postfix: "```"
_content_generated_: 284:md5:f41e6c05922723c1b4d17dab887cfac4
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```
PROGRAM ApproveExpense(claim Report, limit DECIMAL) RETURNS BOOLEAN
    DECLARE total DECIMAL

    total = TOTAL_OF(claim)

    IF total > limit THEN
        REJECT claim, "over the " + limit + " limit"
        RETURN FALSE
    END IF

    APPROVE claim
    RETURN TRUE
END.
```
<!--/INCLUDE-->

You probably read that without effort, and — more to the point — you can tell whether it is
*correct*. Should a claim over the limit be rejected outright, or escalated to a manager? Should the
claimant see the limit in the rejection reason? Those are questions for whoever owns expense policy,
and they can now answer them by reading the rule itself.

That is the entire point of BUBAS. Not that programs are short, but that the person accountable for
a rule can audit the thing that implements it.

## The vocabulary is the language

`TOTAL_OF`, `APPROVE` and `REJECT` are not BUBAS keywords. There are no such keywords. Every word
in that program that means anything to this domain was written, in Java, on purpose:

<!--INCLUDE
from: "../../bubas-doc/src/test/java/javax0/bubas/doc/expense/Expense.java"
start: '// snippet: core-language'
end: '// end snippet'
prefix: "```java"
postfix: "```"
margin: 0
_content_generated_: 554:md5:2d53d8b4eb2a134aca3d385ea99e4955
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```java
/** Stage 1: what the five-minute tutorial shows. */
static BubasLanguage.Builder core() {
    return BubasLanguage.builder()
            .install(Standard::register)
            .defineOpaqueTypeVia("Report", ReportDoc.class)
            .defineFunction("TOTAL_OF", TotalOf.class)
            .defineFunction("NOTE", Note.class)
            .defineStatement("APPROVE {expression/Report:claim}", Approve.class)
            .defineStatement("REJECT {expression/Report:claim}, {expression/STRING:reason}",
                    Reject.class);
}
```
<!--/INCLUDE-->

That is the whole language definition — a handful of operations and a single type. A program
compiled against it can say those things and nothing else.

`Report` is an **opaque type**: BUBAS can hold one, pass it to an operation and store it in a
variable, but it cannot look inside. There is no `claim.employee.manager.email` in this language,
because there is no way to reach into anything. What the domain exposes is what exists.

Notice that the claim arrives as a *parameter*. The application already had it in hand — it is the
application that decided to run this rule on this claim — so it passes the object straight in.
There is no operation for fetching a claim by number, and therefore no "no such claim" case for
the rule to worry about.

## Behind an operation

Each operation is an ordinary Java class with a `call` method. A function returns a value and is
called inside an expression:

<!--INCLUDE
from: "../../bubas-doc/src/test/java/javax0/bubas/doc/expense/Expense.java"
start: '// snippet: total-of'
end: '// end snippet'
prefix: "```java"
postfix: "```"
margin: 0
_content_generated_: 205:md5:af35436ff2553b576098511e17c06623
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```java
@BubasDescription("What a claim comes to in total, in euro.")
public static final class TotalOf {
    public BigDecimal call(Context ctx, Report claim) {
        return claim.total();
    }
}
```
<!--/INCLUDE-->

A command does not, and is written without parentheses at the start of a line:

<!--INCLUDE
from: "../../bubas-doc/src/test/java/javax0/bubas/doc/expense/Expense.java"
start: '// snippet: approve'
end: '// end snippet'
prefix: "```java"
postfix: "```"
margin: 0
_content_generated_: 311:md5:a58808dcfa3ed7ee8efdcb70b4d0af69
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```java
@BubasDescription("Approves a claim for payment.")
public static final class Approve {
    public void call(StatementContext ctx, ExpressionArg claim) {
        final var filed = claim.evaluate().as(Report.class);
        ctx.log("DECISION", "approved " + filed + " for " + filed.total());
    }
}
```
<!--/INCLUDE-->

The description strings are not comments. They are what the language can tell a subject-matter
expert about itself, and BUBAS can export the whole vocabulary as a document for review — the Java
behind an operation being as relevant to that reader as a bicycle is to a fish.

## Running it

Compile once, run as often as you like. Given a limit of 200.00 and the two claims on file:

<!--INCLUDE
from: "../../bubas-doc/target/doc-outputs/stage1-decisions.txt"
prefix: "```"
postfix: "```"
_content_generated_: 240:md5:9e2dc796adea37b002456d7099825fd8
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```
ApproveExpense(claim = report 1 (Alice), limit = 200.00)
    approved report 1 (Alice) for 128.40
    => TRUE

ApproveExpense(claim = report 2 (Bob), limit = 200.00)
    rejected report 2 (Bob) — over the 200.00 limit
    => FALSE
```
<!--/INCLUDE-->

Alice's three receipts come to 128.40 and are approved. Bob's conference and dinner come to 1230.00
and are not.

## What it refuses

Suppose the total is compared against the limit a line too early, before it has been worked out.
Here is what the compiler says — captured from the compiler, not transcribed:

<!--INCLUDE
from: "../../bubas-doc/target/doc-outputs/stage1-untotalled.txt"
prefix: "```"
postfix: "```"
_content_generated_: 94:md5:f83ee1b96cdd26eb0f9f59a6129e28ad
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```
line 4: 'total' is read before it is assigned (at 4:8)
        IF total > limit THEN
```
<!--/INCLUDE-->

This is not a runtime error caught by a test that somebody remembered to write. The program cannot
be run at all. BUBAS has no null, no data structures and one scope, and it refuses to compile a
program that reads a variable before it is set — so an entire family of subtle wrongness has nowhere
to live. Not caught. Absent.

## What it cannot say at all

The program above cannot open a file, reach the network, start a thread, load a class, or touch any
part of your system, and not because something forbids it. There is no word for it. Nothing rejects
`DELETE_ALL_CLAIMS` on policy grounds either — the name simply means nothing, in exactly the way a
typo means nothing.

This is why generated or externally supplied business logic is a different proposition in BUBAS than
in a general-purpose language. The set of things a program can name is finite, written down, and
reviewed before anything is generated at all.

The honest limit: this bounds what can be **named**, not what a named thing **does**. An operation
you expose can do anything Java can do. A vocabulary is only as narrow as the operations you chose.

## Next

The [fifteen-minute tutorial](fifteen-minutes.md) extends this same language — receipts required
above a threshold, per-category limits, and escalation to a manager — and the book continues from
there.

- [`README.md`](../../README.md) — what BUBAS is and how to embed it
- [`SPEC.md`](../../SPEC.md) — the language and the embedding API in full
