# Arrays, and wanting one

<!-- abstract -->
The one aggregate BUBAS has, what it can and cannot do, and a warning. Wanting a data structure is
usually a signal that you have stopped writing a business rule and started writing an algorithm —
which is a reasonable thing to want, and belongs behind an operation rather than in the rule.
<!-- /abstract -->

---

## The one aggregate

BUBAS has arrays. They are the only way to hold more than one value in a single name, and they are
deliberately plain.

An array is declared with its size between the name and the type:

<!--INCLUDE
from: "../../bubas-doc/src/test/resources/programs/category-array.bu"
start:
  pattern: 'DECLARE totals\[2\]'
  include: true
end:
  pattern: 'DECLARE i INTEGER'
  include: false
prefix: "```"
postfix: "```"
margin: 0
_content_generated_: 35:md5:3ddd5a09b67970df5d61205dbf0b3ee2
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```
DECLARE totals[2] DECIMAL
```
<!--/INCLUDE-->

The size can be any `INTEGER` expression and is worked out once, when the declaration runs, so
`DECLARE lines[ITEM_COUNT(claim)] Item` is legal and sizes itself to the claim.

What you can rely on:

- Every element starts at its type's default — zero, empty text, `FALSE` — so an array is never
  half-built.
- `LENGTH(totals)` gives the size.
- Arrays are one-dimensional. There are no arrays of arrays.
- An array cannot be `FINAL`, and neither can an element.
- **Indices start at zero.**

That last point deserves its own paragraph, because the previous chapters said the opposite.

## Two ways of counting, and why

`ITEM_AT(claim, 1)` is the first line of a claim. `totals[0]` is the first element of an array. In
the same program.

That looks like an inconsistency and is actually the distinction the language is built on.
`ITEM_AT` is a domain operation — somebody decided expense claims have lines and that people number
them from one, because they do. An array is a language mechanism, and it counts from zero the way
machine structures have counted from zero for fifty years.

The mismatch is uncomfortable, and it should be. It is the language telling you that you have
stepped out of the domain and into machinery. Which brings us to the point of the chapter.

## A rule that wants an array

Here is a real requirement: meals have one cap, travel has another, and a claim breaching either
goes to a manager. Stage 3 has everything needed to write it — lines, categories, amounts — so let
us write it.

<!--INCLUDE
from: "../../bubas-doc/src/test/resources/programs/category-array.bu"
start:
  pattern: 'FOR i = 1'
  include: true
end:
  pattern: 'END FOR'
  include: true
prefix: "```"
postfix: "```"
margin: 0
_content_generated_: 310:md5:bef565a7ded574f1844a9691bcf266d4
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```
FOR i = 1 TO ITEM_COUNT(claim)
    line = ITEM_AT(claim, i)

    slot = -1
    IF CATEGORY_OF(line) = "meals" THEN
        slot = 0
    ELSEIF CATEGORY_OF(line) = "travel" THEN
        slot = 1
    END IF

    IF slot >= 0 THEN
        totals[slot] = totals[slot] + AMOUNT_OF(line)
    END IF
END FOR
```
<!--/INCLUDE-->

It works. It is also doing several things that should make you uneasy.

The rule now contains a **mapping from category names to array positions**, invented on the spot.
Nothing in the domain says meals are slot 0 and travel is slot 1; a rule made that up, and the next
rule will make up a different one. The two caps arrive as separate parameters and have to be
compared against the right positions — `totals[0]` against `mealCap` — a correspondence held
together by nothing but the author getting it right twice.

Add a third category and you edit the size, the mapping, a parameter, and a comparison, in four
places, with nothing to tell you if you miss one.

And the reviewer — the person this whole language exists for — now has to hold a slot-numbering
scheme in their head to check a policy about dinners.

## The same rule, asked instead

The vocabulary gains one operation:

<!--INCLUDE
from: "../../bubas-doc/target/doc-outputs/vocabulary-added-4.md"
_content_generated_: 118:md5:54d9fcd3abda510216d9982b1e4a7946
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
### TOTAL_FOR(claim Report, category STRING) -> DECIMAL

What one category of spending on a claim comes to, in euro.
<!--/INCLUDE-->

And the rule becomes the whole of this:

<!--INCLUDE
from: "../../bubas-doc/src/test/resources/programs/category-caps.bu"
prefix: "```"
postfix: "```"
_content_generated_: 440:md5:3b1374943cd3a042fb9bf6d844a625c3
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```
PROGRAM CategoryCaps(claim Report, mealCap DECIMAL, travelCap DECIMAL) RETURNS BOOLEAN
    IF TOTAL_FOR(claim, "meals") > mealCap THEN
        ESCALATE claim, "meals came to " + TOTAL_FOR(claim, "meals")
        RETURN FALSE
    END IF

    IF TOTAL_FOR(claim, "travel") > travelCap THEN
        ESCALATE claim, "travel came to " + TOTAL_FOR(claim, "travel")
        RETURN FALSE
    END IF

    APPROVE claim
    RETURN TRUE
END.
```
<!--/INCLUDE-->

No array, no loop, no mapping, no positional correspondence. A reader who knows nothing about
programming can check it against the policy document line by line.

Both versions are compiled and run against the same claims by the same test, which asserts they
reach identical decisions and produce identical messages. This is what they both say:

<!--INCLUDE
from: "../../bubas-doc/target/doc-outputs/stage4-by-asking.txt"
prefix: "```"
postfix: "```"
_content_generated_: 409:md5:5eb0d72c740354311a5bc3a4c73813bf
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```
CategoryCaps(claim = report 1 (Alice), mealCap = 60.00, travelCap = 500.00)
    approved report 1 (Alice) for 128.40
    => TRUE

CategoryCaps(claim = report 4 (Carol), mealCap = 60.00, travelCap = 500.00)
    escalated report 4 (Carol) — meals came to 75.00
    => FALSE

CategoryCaps(claim = report 2 (Erin), mealCap = 60.00, travelCap = 500.00)
    approved report 2 (Erin) for 450.00
    => TRUE
```
<!--/INCLUDE-->

Nothing was given up. The work did not vanish — it moved behind `TOTAL_FOR`, where it is written
once, tested once, and named in the language of the domain.

## The test

You will not always have someone available to add an operation the moment you want one, so it helps
to recognise the signal early. **Wanting an array is usually the signal.**

More precisely, ask what the array is for. If you are accumulating something across the lines of a
claim in order to consult it afterwards, you are building an intermediate result, and an
intermediate result is a thing the domain has no name for. That is the definition of having left
business logic.

A few shapes that mean the same thing:

- Building up values to compare against each other at the end
- Sorting anything
- Comparing every line against every other line
- Keeping several running figures and combining them afterwards
- Any mapping from a domain name to a number you invented

None of these are forbidden and none of them are wrong. They are all *algorithms*, and algorithms
belong behind an operation, written in Java by somebody who can test them properly — which is
chapter 23's subject.

## When an array is the right answer

Not never, or the language would not have them.

The honest cases are those where the domain itself is plural and the operations hand you the plural
thing. If a vocabulary offers an operation that fills an array of approvers, and your rule genuinely
has to notify each of them, then an array holding approvers is exactly the right shape, and looping
over it is not a smell. The array is carrying a domain concept, not an invented one.

The distinction is whether the collection came *from* the domain or was assembled *by* your rule.
The first is fine. The second is the signal.

## What is coming

The vocabulary has grown from three operations to a dozen without ever changing one, which is what
the last several chapters have quietly been demonstrating.

The next chapter adds an operation unlike any so far: one that does not calculate an answer but asks
a model for an opinion, and will not necessarily give the same one twice.
