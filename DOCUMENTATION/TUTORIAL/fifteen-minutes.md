# BUBAS in Fifteen Minutes

The [five-minute tutorial](five-minutes.md) showed a language with three operations and one type,
and a rule that answered yes or no. Real expense policy is not that tidy: some claims go to a
manager, some lines need receipts, and some categories have their own ceiling.

This page grows that same language and that same rule until it could plausibly be the real one.
Every fragment below is pulled from code the build compiles and runs.

---

## Where we left off

<!--INCLUDE
from: "../../bubas-doc/src/test/resources/programs/approve-expense.bu"
start:
  pattern: 'total = TOTAL_OF'
  include: true
end:
  pattern: 'APPROVE claim'
  include: true
prefix: "```"
postfix: "```"
margin: 0
_content_generated_: 144:md5:dcf14742603ae3abb4a4344350613662
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```
total = TOTAL_OF(claim)

IF total > limit THEN
    REJECT claim, "over the " + limit + " limit"
    RETURN FALSE
END IF

APPROVE claim
```
<!--/INCLUDE-->

Two outcomes, one threshold. Everything from here is added to that, never rewritten.

## Three outcomes, not two

A claim over someone's personal limit is not fraudulent, it is just not theirs to approve. It
should go to a manager. Something ten times the limit is a different matter and should come back
with a business case attached.

That is a third outcome, so it is a third operation:

<!--INCLUDE
from: "../../bubas-doc/src/test/java/javax0/bubas/doc/expense/Expense.java"
start: '// snippet: escalating-language'
end: '// end snippet'
prefix: "```java"
postfix: "```"
margin: 0
_content_generated_: 269:md5:93d1b331c1f7991a47399e281160c1c0
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```java
/** Stage 2: one more outcome, so a rule need not choose between yes and no. */
static BubasLanguage.Builder escalating() {
    return core().defineStatement(
            "ESCALATE {expression/Report:claim}, {expression/STRING:reason}", Escalate.class);
}
```
<!--/INCLUDE-->

Notice that stage two does not restate stage one. It calls it. The five-minute tutorial and this
one are looking at the same definitions rather than at two copies that have to be kept in step.

The rule now has three branches:

<!--INCLUDE
from: "../../bubas-doc/src/test/resources/programs/escalating-expense.bu"
prefix: "```"
postfix: "```"
_content_generated_: 458:md5:d4fce8d0ecc59758e86fda1c86fde897
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```
PROGRAM ApproveExpense(claim Report, limit DECIMAL) RETURNS BOOLEAN
    DECLARE total DECIMAL
    DECLARE ceiling DECIMAL FINAL = 1000.00

    total = TOTAL_OF(claim)

    IF total > ceiling THEN
        REJECT claim, "over " + ceiling + " — needs a business case first"
        RETURN FALSE
    ELSEIF total > limit THEN
        ESCALATE claim, "over the " + limit + " limit"
        RETURN FALSE
    END IF

    APPROVE claim
    RETURN TRUE
END.
```
<!--/INCLUDE-->

Three things arrived at once.

**`ELSEIF` is one word.** `ELSE IF`, `ELIF` and `ELSIF` are all rejected. Because BUBAS matches
statements a whole line at a time, `ELSE IF x THEN` is simply two statements crowded onto one line,
and is refused without needing a rule of its own.

**`FINAL` makes a constant.** `ceiling` is set once at its declaration and can never be assigned
again; try it and the program will not compile. Policy numbers are exactly the thing you want
nailed down where a reader can see them.

**A number goes into a message with `+`.** There is no formatting language to learn.

Run it against three claims — one comfortable, one over the limit, one over the ceiling:

<!--INCLUDE
from: "../../bubas-doc/target/doc-outputs/stage2-decisions.txt"
prefix: "```"
postfix: "```"
_content_generated_: 389:md5:b459d26baac3b5b92a63131df4ff1b4f
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```
ApproveExpense(claim = report 1 (Alice), limit = 200.00)
    approved report 1 (Alice) for 128.40
    => TRUE

ApproveExpense(claim = report 2 (Erin), limit = 200.00)
    escalated report 2 (Erin) — over the 200.00 limit
    => FALSE

ApproveExpense(claim = report 3 (Frank), limit = 200.00)
    rejected report 3 (Frank) — over 1000.00 — needs a business case first
    => FALSE
```
<!--/INCLUDE-->

## Inside the claim

So far a claim has been a single number. Receipts and per-category caps need the lines themselves,
so the language gains a second opaque type and the operations to walk it:

<!--INCLUDE
from: "../../bubas-doc/src/test/java/javax0/bubas/doc/expense/Expense.java"
start: '// snippet: itemised-language'
end: '// end snippet'
prefix: "```java"
postfix: "```"
margin: 0
_content_generated_: 567:md5:8df225b1f8617eb376f3991b3177e19e
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```java
/** Stage 3: the claim stops being a single number and becomes a list of lines. */
static BubasLanguage.Builder itemised() {
    return escalating()
            .defineOpaqueType("Item", Item.class)
            .defineFunction("ITEM_COUNT", ItemCount.class)
            .defineFunction("ITEM_AT", ItemAt.class)
            .defineFunction("AMOUNT_OF", AmountOf.class)
            .defineFunction("CATEGORY_OF", CategoryOf.class)
            .defineFunction("MERCHANT_OF", MerchantOf.class)
            .defineFunction("HAS_RECEIPT", HasReceipt.class);
}
```
<!--/INCLUDE-->

`Item` is opaque exactly as `Report` is. A program can hold one and ask questions about it, and
that is all. There is no `line.merchant.taxId`, because there is no way to reach into anything.

The operation that hands one over is an ordinary function taking two arguments:

<!--INCLUDE
from: "../../bubas-doc/src/test/java/javax0/bubas/doc/expense/Expense.java"
start: '// snippet: item-at'
end: '// end snippet'
prefix: "```java"
postfix: "```"
margin: 0
_content_generated_: 255:md5:47d9b6bf3368c90dfcea9c7639233682
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```java
@BubasDescription("The line at a position on a claim. The first line is line 1.")
public static final class ItemAt {
    public Item call(Context ctx, Report claim, long position) {
        return claim.items.get((int) position - 1);
    }
}
```
<!--/INCLUDE-->

Now the rule can say what expense policy actually says:

<!--INCLUDE
from: "../../bubas-doc/src/test/resources/programs/itemised-expense.bu"
prefix: "```"
postfix: "```"
_content_generated_: 1124:md5:49efedf6684ce961fa9cddc187d09389
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```
PROGRAM ApproveExpense(claim Report, limit DECIMAL) RETURNS BOOLEAN
    DECLARE total DECIMAL
    DECLARE meals DECIMAL = 0.0
    DECLARE i INTEGER
    DECLARE line Item
    DECLARE ceiling DECIMAL FINAL = 1000.00
    DECLARE mealCap DECIMAL FINAL = 60.00
    DECLARE receiptFloor DECIMAL FINAL = 25.00

    FOR i = 1 TO ITEM_COUNT(claim)
        line = ITEM_AT(claim, i)

        IF AMOUNT_OF(line) > receiptFloor AND NOT HAS_RECEIPT(line) THEN
            REJECT claim, "no receipt for " + MERCHANT_OF(line)
            RETURN FALSE
        END IF

        IF CATEGORY_OF(line) = "meals" THEN
            meals = meals + AMOUNT_OF(line)
        END IF
    END FOR

    IF meals > mealCap THEN
        ESCALATE claim, "meals came to " + meals + ", the cap is " + mealCap
        RETURN FALSE
    END IF

    total = TOTAL_OF(claim)

    IF total > ceiling THEN
        REJECT claim, "over " + ceiling + " — needs a business case first"
        RETURN FALSE
    ELSEIF total > limit THEN
        ESCALATE claim, "over the " + limit + " limit"
        RETURN FALSE
    END IF

    APPROVE claim
    RETURN TRUE
END.
```
<!--/INCLUDE-->

**`FOR i = 1 TO ITEM_COUNT(claim)`** counts from one, because the first line on an expense claim
is line 1. Nothing here is an array — `ITEM_AT` is a business-level question, and it answers the
way the person asking it would count. Zero-based indexing is a convention that leaks out of how
arrays are laid out in memory, and since the language never exposes an array, it has nothing to
leak out of. The bounds are worked out once on entry rather than on every pass, and `EXIT FOR`
leaves early when you need it.

**`AND`, `OR` and `NOT`** read as words. `NOT` binds tightest, then comparisons, then `AND`, then
`OR`, so `AMOUNT_OF(line) > receiptFloor AND NOT HAS_RECEIPT(line)` groups the way it reads.

**`=` compares.** There is no `==`. A single `=` means assignment at the start of a statement and
comparison inside an expression, and those two places never overlap.

**The thresholds live in the program.** `mealCap`, `receiptFloor` and `ceiling` are three numbers
on three lines that a finance manager can read, question and change. That is not an accident of
this example; it is the whole reason for putting the rule in a small language rather than behind a
Java method called `applyPolicy`.

Five claims, one for each way the rule can end:

<!--INCLUDE
from: "../../bubas-doc/target/doc-outputs/stage3-decisions.txt"
prefix: "```"
postfix: "```"
_content_generated_: 657:md5:bf44462df9db0aaf7b6974976b165fb3
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```
ApproveExpense(claim = report 1 (Alice), limit = 200.00)
    approved report 1 (Alice) for 128.40
    => TRUE

ApproveExpense(claim = report 5 (Dave), limit = 200.00)
    rejected report 5 (Dave) — no receipt for City Taxi
    => FALSE

ApproveExpense(claim = report 4 (Carol), limit = 200.00)
    escalated report 4 (Carol) — meals came to 75.00, the cap is 60.00
    => FALSE

ApproveExpense(claim = report 2 (Erin), limit = 200.00)
    escalated report 2 (Erin) — over the 200.00 limit
    => FALSE

ApproveExpense(claim = report 3 (Frank), limit = 200.00)
    rejected report 3 (Frank) — over 1000.00 — needs a business case first
    => FALSE
```
<!--/INCLUDE-->

Carol's claim is worth a second look. It totals 75.00, comfortably under the 200.00 limit, and is
still not approved — the meals cap caught it first. **The order of the checks is part of the
policy**, and it is visible on the page rather than buried in a method somewhere. Whether meals
should be checked before or after the total is a question for whoever owns the policy, and now they
can answer it.

## One namespace, case-insensitive

The loop variable is called `line`, not `item`, and that is not a style choice.

Variable names, function names and opaque type names all share one namespace, and names are unique
**case-insensitively**. `Item` is a registered type, so `item` is taken — as are `ITEM` and `iTeM`.
A reference must then match the declaration character for character, which rules out
`userId`/`UserID` lookalikes and capitalisation typos in a single rule.

Worth knowing before you name your own vocabulary: pick type names you will not also want as
variables.

## What it refuses

Two opaque types are two types. Asking a line-item question about a whole claim does not compile:

<!--INCLUDE
from: "../../bubas-doc/target/doc-outputs/stage3-wrong-type.txt"
prefix: "```"
postfix: "```"
_content_generated_: 104:md5:13ed0ed4b94f92caa1042f9d34790e46
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```
line 4: AMOUNT_OF takes Item for 'line', but was given Report
        spent = AMOUNT_OF(claim)
```
<!--/INCLUDE-->

The message names the type it wanted, the type it got, and the parameter it wanted it for. That
last part comes from the Java parameter being called `line`; naming it `x` would have produced a
worse message for a reader who will never see the Java.

Along with what the five-minute tutorial showed — a variable read before it is set — this is the
shape of most BUBAS diagnostics: a compile-time refusal naming the line, rather than a runtime
surprise in front of a customer.

## What you now know

Enough to read and change a real rule: three outcomes, constants, loops, boolean logic, two opaque
types, and where the compiler will stop you.

What is left is mostly about building the vocabulary rather than writing programs in it — how
opaque types and services are wired up, how a vocabulary is exported for review by the people who
own the rules, and how programs are unit tested in BUBAS itself with the operations mocked out.

- [`README.md`](../../README.md) — what BUBAS is and how to embed it
- [`SPEC.md`](../../SPEC.md) — the language and the embedding API in full
