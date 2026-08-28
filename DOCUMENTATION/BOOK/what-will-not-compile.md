# What will not compile

<!-- abstract -->
The refusals: a value read before it has been worked out, a constant assigned twice, a variable
declared and never used. This chapter argues that these are not the compiler being fussy — with no
null, one scope and no data structures, whole families of mistake have nowhere left to live. They
are not caught. They are absent.
<!-- /abstract -->

---

## Not caught — absent

Every language has a list of mistakes it catches. What matters more is the list of mistakes it
cannot make.

A rule that reads a total before working it out is not a bug BUBAS finds for you. It is a program
that does not exist: it cannot be run, cannot be deployed, cannot reach a claimant. The distinction
sounds like hair-splitting until you have spent an afternoon on a production incident caused by
something that was, technically, caught — by a test somebody happened to write.

This chapter is the list. It is shorter than experience suggests it should be, and the last section
explains why.

## A value you have not worked out yet

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

Declaring a variable does not give it a value. It reserves the name and the type, and until
something assigns to it, reading it is an error.

Not zero. Not empty text. Not null. There is no value that means *not worked out yet*, so there is
no way to accidentally compute with one, and no rule anywhere in your organisation that quietly
treats a missing figure as nought.

The compiler traces every path through the program to prove this, which means it also catches the
subtler version: a value assigned in one branch of an `IF` and read after the `END IF`, where the
other branch never set it. That reads as obviously fine when you write it and is obviously wrong
when you draw the two paths out, which is what the compiler does for you.

## Why there are four kinds of loop

Here is the same tracing making a distinction that looks pedantic and is not:

<!--INCLUDE
from: "../../bubas-doc/target/doc-outputs/stage3-top-tested.txt"
prefix: "```"
postfix: "```"
_content_generated_: 135:md5:5a96df68f92069a616cfc41dd4eaac7a
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```
line 14: 'lastMerchant' is read before it is assigned (at 14:24)
        NOTE "last was " + lastMerchant + ", limit " + limit
```
<!--/INCLUDE-->

The loop sets `lastMerchant` on every pass. But a `DO WHILE` tests its condition *before* the first
pass, so a claim with no lines runs the body zero times, and `lastMerchant` is read having never
been set. The compiler will not take the chance, and it is right not to: an empty claim is exactly
the input nobody tests by hand.

Move the test to the bottom and the same program compiles:

<!--INCLUDE
from: "../../bubas-doc/src/test/resources/programs/bottom-tested-loop.bu"
start:
  pattern: 'DO$'
  include: true
end:
  pattern: 'END DO WHILE'
  include: true
prefix: "```"
postfix: "```"
margin: 0
_content_generated_: 128:md5:62718c3e4f88377a3f5101038e3f18aa
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```
DO
    line = ITEM_AT(claim, i)
    lastMerchant = MERCHANT_OF(line)
    i = i + 1
END DO WHILE i <= ITEM_COUNT(claim)
```
<!--/INCLUDE-->

The body always runs at least once, so the assignment is guaranteed, and the compiler can see it.

That is what the four loop shapes are for. They are not stylistic variants; the position of the
condition changes what the compiler can prove.

## A constant is constant

<!--INCLUDE
from: "../../bubas-doc/target/doc-outputs/stage3-final.txt"
prefix: "```"
postfix: "```"
_content_generated_: 82:md5:1c4cbbdaa9c8ad233f5dd7e217a4ed92
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```
line 4: 'mealCap' is final and cannot be changed
        mealCap = limit
```
<!--/INCLUDE-->

`FINAL` on a declaration means the value is fixed at that line and never changes again. Parameters
are final too — nothing can reassign what the program was given.

This matters more in a business rule than in ordinary code. The policy numbers in a rule — a cap, a
floor, a threshold — are the part a reviewer reads most carefully, and `FINAL` is a promise that
what they read at the top is what applied at the bottom. A rule that adjusts its own cap partway
down is one nobody can review by reading it once.

## Every path must answer

<!--INCLUDE
from: "../../bubas-doc/target/doc-outputs/stage3-missing-return.txt"
prefix: "```"
postfix: "```"
_content_generated_: 175:md5:3aebe11874f5af37005b011b61a2c437
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```
line 1: this program declares RETURNS BOOLEAN but can reach its end without returning a value
    PROGRAM ApproveExpense(claim Report, limit DECIMAL) RETURNS BOOLEAN
```
<!--/INCLUDE-->

A program that says it answers `BOOLEAN` must answer on every path out of it. Falling off the end is
not an implicit no.

The common shape of this mistake is a chain of tests where every branch returns except the one
nobody thought about, which is usually the ordinary case. Here the program says so at compile time
instead of returning something unhelpful in production.

## The loop counts, and you do not

<!--INCLUDE
from: "../../bubas-doc/target/doc-outputs/stage3-loop-variable.txt"
prefix: "```"
postfix: "```"
_content_generated_: 117:md5:e4b18b6610c883e5bf1632d929d20dc1
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```
line 9: 'i' is the variable of an enclosing FOR loop and cannot be assigned inside it
            i = i + 1
```
<!--/INCLUDE-->

Inside a `FOR`, the counter belongs to the loop. Skipping ahead by assigning to it — a classic way
to make a loop that reads as though it visits every line and does not — is refused outright. If you
want to skip lines, test for the ones you want and leave the counting alone.

## Types, including the ones BUBAS cannot see

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

Numbers, text and booleans are checked as you would expect. So are the domain values, and that is
the interesting part: BUBAS has no idea what a `Report` or an `Item` *is*, and still knows they are
not interchangeable. It knows because somebody registered two different names, and two names are
two types.

Note what the message gives you: the type wanted, the type supplied, and the name of the thing it
was wanted for. That last part comes from the vocabulary rather than from your program, which is one
of several places in this book where the care taken in Part 3 shows up as a better experience in
Part 1.

## An answer nobody keeps

<!--INCLUDE
from: "../../bubas-doc/target/doc-outputs/stage1-discarded.txt"
prefix: "```"
postfix: "```"
_content_generated_: 144:md5:865029a0d72bdb266e312932e7c8e2d5
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```
line 4: TOTAL_OF returns a value, so it cannot stand alone as a statement; a result would be discarded silently
        TOTAL_OF claim
```
<!--/INCLUDE-->

Asking a question and dropping the answer is refused, so a line that looks like it does something
always does. Chapter 4 covered this; it belongs on the list.

## A variable nobody reads

Declaring a variable and never reading it is also an error — `'headroom' is declared but never
read`.

This is the refusal people push back on hardest, and it earns its place. In a business rule, an
unread variable is nearly always the fossil of a policy change: a figure that used to be compared
against something, left behind when the comparison moved or was deleted. It is a reviewer's false
friend, because it reads as though it still participates. Removing it is a two-second edit; leaving
it is a rule that lies to the next person.

## What is not on the list

The refusals above are the interesting half. The other half is a set of mistakes that never come up
at all, because nothing in the language can express them.

There is no null, so there is no dereferencing one. There is no array indexing on a domain value, so
there is no reading past the end of one. There are no nested scopes, so no variable quietly shadows
another and no rule reads the wrong one of two things with the same name. There are no data
structures, so nothing can be aliased, mutated by somebody else's code, or left in a half-built
state. There is no inheritance, so nothing calls an override you did not know existed. There is no
concurrency in a program, so nothing races.

None of these are caught. There is nothing to catch.

## Why the list is short

It would be easy to read this chapter as a language being strict. That is not quite it.

Most of what a compiler for a large language checks is defending against the size of the language.
Null checks exist because there is null. Escape analysis exists because there are references.
Immutability rules exist because there is shared mutable state. Take those away and the checks are
not passed — they become unnecessary.

What is left is a short list of things that are genuinely about the rule: did you work out this
figure before using it, does every path answer, is the constant constant. Those are questions the
person who owns the policy would ask too, if they knew to ask them. Everything else the compiler
would have had to check has been designed out.

## What is coming

One thing on this list has been deferred twice now: the single aggregate BUBAS does have. The next
chapter is about arrays — what they can do, and why wanting one is usually a signal worth listening
to.
