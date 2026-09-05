# Repeating

<!-- abstract -->
Walking the lines of a claim: `FOR`, `DO WHILE`, `DO UNTIL`, and leaving early with `EXIT`.
Counting starts at one, because the first line on an expense claim is line 1 and nothing here is an
array.
<!-- /abstract -->

---

## The claim opens up

Until now a claim has been a single number: whatever `TOTAL_OF` said it came to. Real expense
policy is not written that way. Receipts are required per line, not per claim. Meals have their own
cap. A single hotel bill can be fine while the same money spent on dinners is not.

So the vocabulary gains a second kind of value — one line on a claim — and the operations to walk
them:

<!--INCLUDE
from: "../../bubas-doc/target/doc-outputs/vocabulary-added-3.md"
_content_generated_: 669:md5:10d7f22e3f881985b4de76d42c87a4f6
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
### Item

A single line on a claim: what was bought, from whom, for how much.
Ask AMOUNT_OF, CATEGORY_OF, MERCHANT_OF and HAS_RECEIPT about one.

### ITEM_COUNT(claim Report) -> INTEGER

How many lines a claim has.

### ITEM_AT(claim Report, position INTEGER) -> Item

The line at a position on a claim. The first line is line 1.

### AMOUNT_OF(line Item) -> DECIMAL

What a single line came to, in euro.

### CATEGORY_OF(line Item) -> STRING

What kind of spending a line is: travel, meals, lodging.

### MERCHANT_OF(line Item) -> STRING

Who the money was paid to on a line.

### HAS_RECEIPT(line Item) -> BOOLEAN

Whether the claimant attached a receipt to a line.
<!--/INCLUDE-->

That is the language describing itself. Every BUBAS vocabulary can produce a document like this,
written for whoever will use it rather than for whoever built it — chapter 11 is about reading
one. `Item` arrived the same way `Report` did: somebody decided this domain has lines, and made
them askable.

An `Item` is opaque exactly as a `Report` is. You can hold one and ask it questions. You cannot open
it, print it, or compare two of them.

## Counting through the lines

<!--INCLUDE
from: "../../bubas-doc/src/test/resources/programs/itemised-expense.bu"
start:
  pattern: 'FOR i = 1'
  include: true
end:
  pattern: 'END FOR'
  include: true
prefix: "```"
postfix: "```"
margin: 0
_content_generated_: 331:md5:047a5c56df1aacf649edd3737be3e777
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```
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
```
<!--/INCLUDE-->

`FOR` takes a variable, a start, and an end, and runs the body once for each value from one to the
other inclusive.

**Counting starts at one.** The first line on a claim is line 1, because that is what it is called
by everyone who has ever looked at an expense claim. Zero-based counting is a convention that leaks
out of how arrays are laid out in memory, and since this language never exposes an array, it has
nothing to leak out of. `ITEM_AT(claim, 1)` is the first line.

Four smaller facts, each of which will eventually matter:

- The loop variable must be declared, as an `INTEGER`, before the loop.
- The start and end are worked out once, on entry. Changing what `ITEM_COUNT` would answer partway
  through does not lengthen the loop.
- The body may not assign the loop variable. Counting is the loop's job.
- `STEP` changes the stride, and may be negative: `FOR i = 10 TO 0 STEP -2`.

After the loop, the variable holds the first value that failed the test — which is occasionally
useful and more often a trap, so prefer to carry anything you need out in a variable of your own.

## Stopping early

A loop that has found what it was looking for should stop looking:

<!--INCLUDE
from: "../../bubas-doc/src/test/resources/programs/first-big-item.bu"
start:
  pattern: 'FOR i = 1'
  include: true
end:
  pattern: 'END FOR'
  include: true
prefix: "```"
postfix: "```"
margin: 0
_content_generated_: 159:md5:8e938a1dfba4493859fe73beea6079c7
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```
FOR i = 1 TO ITEM_COUNT(claim)
    line = ITEM_AT(claim, i)
    IF AMOUNT_OF(line) > limit THEN
        found = i
        EXIT FOR
    END IF
END FOR
```
<!--/INCLUDE-->

`EXIT FOR` leaves the innermost enclosing `FOR`. There are no labels and no way to leave two loops
at once; if you need that, carry a variable and test it.

Notice what the rule does with `found`. The loop records *which* line tripped it and gets out; the
decision is made afterwards, in the open, where a reader can see it. Making the decision inside the
loop would have worked too, and would have been harder to review.

<!--INCLUDE
from: "../../bubas-doc/target/doc-outputs/stage3-exit-for.txt"
prefix: "```"
postfix: "```"
_content_generated_: 253:md5:7fc9ed07e779366998739f35d6a30529
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```
FirstBigItem(claim = report 1 (Alice), limit = 200.00)
    approved report 1 (Alice) for 128.40
    => TRUE

FirstBigItem(claim = report 2 (Erin), limit = 200.00)
    escalated report 2 (Erin) — line 1 is over the limit on its own
    => FALSE
```
<!--/INCLUDE-->

## When you are not counting

`FOR` is for when you know how many times. When the answer is "until something is true", the other
form fits better:

<!--INCLUDE
from: "../../bubas-doc/src/test/resources/programs/running-total.bu"
start:
  pattern: 'DO WHILE'
  include: true
end:
  pattern: 'END DO'
  include: true
prefix: "```"
postfix: "```"
margin: 0
_content_generated_: 152:md5:dca3d1aca216038b1ea12840e8e6acab
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```
DO WHILE i <= ITEM_COUNT(claim) AND running <= limit
    line = ITEM_AT(claim, i)
    running = running + AMOUNT_OF(line)
    i = i + 1
END DO
```
<!--/INCLUDE-->

This one watches two things at once — how far through the claim it is, and whether the running
total has already passed the limit — and stops on whichever happens first. A `FOR` could not express
that without an `EXIT`.

<!--INCLUDE
from: "../../bubas-doc/target/doc-outputs/stage3-do-while.txt"
prefix: "```"
postfix: "```"
_content_generated_: 245:md5:3421b6a95b8bce31933fbce55d64cc00
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```
RunningTotal(claim = report 1 (Alice), limit = 200.00)
    approved report 1 (Alice) for 128.40
    => TRUE

RunningTotal(claim = report 4 (Carol), limit = 50.00)
    escalated report 4 (Carol) — passed the limit at line 2
    => FALSE
```
<!--/INCLUDE-->

There are four shapes in total, and the condition may sit at either end:

- `DO WHILE condition` … `END DO` — tested before each pass, so the body may never run
- `DO UNTIL condition` … `END DO` — the same, negated
- `DO` … `END DO WHILE condition` — tested after each pass, so the body always runs once
- `DO` … `END DO UNTIL condition` — the same, negated

`EXIT DO` leaves a `DO` loop the way `EXIT FOR` leaves a `FOR`, and each only leaves a loop of its
own kind.

The bottom-tested forms exist for a reason beyond taste, which chapter 8 explains: a loop whose body
always runs at least once can satisfy the compiler that a variable has been set, and a top-tested
loop cannot.

## Everything together

The full stage-3 rule reads a claim line by line, checks receipts as it goes, accumulates the meals
separately, and only then considers the total:

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

Five claims, five different endings, and one of them worth arguing about. Carol's claim comes to
75.00 against a limit of 200.00 and is still not approved, because the meals cap is tested before
the total and it never gets that far. Whether that is the right policy is exactly the kind of
question the last chapter said belongs to the person who owns the rule — and here it is a single
line's position on a page rather than an ordering buried in three methods.

## The thing to watch

A loop is where a business rule most easily stops being a business rule.

If you find a loop building something up over many lines, sorting, comparing every line against
every other, or keeping several running values at once and combining them at the end, stop and look
at it. What you are writing may be an algorithm that wants to live behind a single operation, asked
once. Chapter 9 gives that instinct a name and a test.

## A loop has to be able to run, and to stop

Loops carry the same requirement as an `IF`, in two directions.

A loop that cannot run at all is refused: `FOR line = 5 TO 1` counts backwards without being told
to, and its body is text nobody will ever execute. So is a step of zero, which would never finish.
So is a `DO WHILE` whose condition is decided before the first pass.

There is a subtler consequence of the same idea. If the compiler can see every value a loop turns
on, it works the loop out rather than guessing — a loop counting from five up to seven leaves seven,
and a test on that afterwards is a test with an answer, and refused like any other. This is only
ever true of a loop whose values are all written in the rule itself. The moment one of them comes
from outside — a parameter, an operation, anything the compiler cannot read off the page — the loop
becomes opaque again and everything after it is a real question.

So is a loop with nothing in it. A body you have emptied out is a shape left behind after the
contents were deleted, and the only reading under which it means anything is one where the work
happens inside the condition — which is the kind of cleverness a rule should never contain. The same
goes for an `IF` arm and an `ELSE`: write what belongs there, or delete the construct.

A loop that cannot stop is refused too — a condition that stays true with no `EXIT` anywhere inside
it. `DO WHILE TRUE` is perfectly good BUBAS as long as something in the body leaves; what is refused
is the version where nothing does.

Both come from the same idea as the rest of this chapter: the bounds of a loop are the part a
reviewer checks, and a loop whose bounds settle the matter before the first pass is not a loop. The
compiler says so rather than running it zero times and moving on.

## What is coming

You have now seen every way a BUBAS program can be put together. The next chapter is the one that
changes how the rest of the book reads: what the compiler refuses, and why the list of things that
can go wrong is shorter than experience has taught you to expect.
