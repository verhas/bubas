# Asking and telling

<!-- abstract -->
Two kinds of operation, distinguished on sight. A function is asked a question inside an expression
and answers with a value; a command is told to do something and stands alone on its line. Why the
distinction is worth making, and how to tell which one you are looking at.
<!-- /abstract -->

---

## Two things a program does

A rule does two kinds of thing. It finds things out, and it makes things happen.

Finding out what a claim comes to, whether a receipt is attached, how many days the trip lasted —
those are questions, and a question has an answer you then do something with. Approving the claim,
refusing it, sending it to a manager, recording a note — those are not questions. There is nothing
to do with the result, because the point was the doing.

BUBAS keeps these apart, and writes them differently, so you can tell at a glance which one you are
looking at.

## Asking

An operation that answers a question is written with brackets and used inside an expression:

<!--INCLUDE
from: "../../bubas-doc/src/test/resources/programs/approve-expense.bu"
start:
  pattern: 'total = TOTAL_OF'
  include: true
end:
  pattern: 'IF total'
  include: false
prefix: "```"
postfix: "```"
margin: 0
_content_generated_: 34:md5:1b340b228e393245380b25f6ba59ff1c
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```
total = TOTAL_OF(claim)

```
<!--/INCLUDE-->

The brackets are not optional here, even when there is nothing to put in them. An asking operation
with no arguments is still written `SOMETHING()`, because the brackets are what say *this is a
call* rather than *this is a variable*.

The answer has to go somewhere. Usually into a variable, as above, but anywhere a value is welcome
will do — inside a comparison, as an argument to something else, as the thing you return.

## Telling

An operation that makes something happen stands alone as a whole line:

<!--INCLUDE
from: "../../bubas-doc/src/test/resources/programs/approve-expense.bu"
start:
  pattern: 'APPROVE claim'
  include: true
end:
  pattern: 'END\.'
  include: false
prefix: "```"
postfix: "```"
margin: 0
_content_generated_: 35:md5:d73ddd99f54b22113242003dbfc9e358
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```
APPROVE claim
RETURN TRUE
```
<!--/INCLUDE-->

`APPROVE claim` is one such line. It takes one thing and needs no punctuation at all. Where a
telling operation takes more than one thing, they are separated by commas — `REJECT claim, "over
the limit"` — and still no brackets.

There is nothing to assign, because there is no answer. `APPROVE` does not report whether it
worked; if it cannot do its job it stops the program, and if it can, the program carries on.

## The brackets are optional when telling

Some telling operations accept brackets as well. Both of these lines call the same operation, in
the same program:

<!--INCLUDE
from: "../../bubas-doc/src/test/resources/programs/noted-decision.bu"
start:
  pattern: 'NOTE "checked against'
  include: true
end:
  pattern: 'IF total >'
  include: false
prefix: "```"
postfix: "```"
margin: 0
_content_generated_: 53:md5:4dd8a583edf70e5d0467b057249644f6
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```
NOTE "checked against a limit of " + limit

```
<!--/INCLUDE-->

<!--INCLUDE
from: "../../bubas-doc/src/test/resources/programs/noted-decision.bu"
start:
  pattern: 'NOTE\("over by'
  include: true
end:
  pattern: 'REJECT claim'
  include: false
prefix: "```"
postfix: "```"
margin: 0
_content_generated_: 44:md5:80361d199f8be5f425be24378c20c7e6
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```
NOTE("over by " + (total - limit))
```
<!--/INCLUDE-->

Running it shows both notes coming out in order, alongside the decision:

<!--INCLUDE
from: "../../bubas-doc/target/doc-outputs/stage1-notes.txt"
prefix: "```"
postfix: "```"
_content_generated_: 338:md5:3520dda260293311f1189c053685433f
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```
ApproveWithNote(claim = report 1 (Alice), limit = 200.00)
    checked against a limit of 200.00
    approved report 1 (Alice) for 128.40
    => TRUE

ApproveWithNote(claim = report 2 (Bob), limit = 200.00)
    checked against a limit of 200.00
    over by 1030.00
    rejected report 2 (Bob) — over the 200.00 limit
    => FALSE
```
<!--/INCLUDE-->

Write whichever reads better. `NOTE "checked the limit"` reads like an instruction, which is what it
is; `NOTE("over by " + (total - limit))` puts brackets round a longer expression and is easier to
scan for it. Nothing depends on the choice.

The reason it is allowed only in this direction is worth a sentence, and it is not ambiguity.
BUBAS has one namespace, so `TOTAL_OF` is either an operation or a variable and never both; the
compiler always knows which. The brackets are for the reader. Inside an expression, where a name
could as easily be a value somebody stored earlier, `TOTAL_OF(claim)` says *this is being worked
out here* at a glance. A telling operation is a whole line of its own, so a reader is in no doubt
about it either way, and the language does not insist.

## An answer cannot be thrown away

The one thing you cannot do is ask a question and ignore what comes back:

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

This is a small rule with a real purpose. In most languages, calling something for its answer and
then discarding it is legal, and it is a classic way for a bug to hide: the call looks like it did
something, and it did nothing anybody kept. Here, if an operation answers, its answer has to go
somewhere, so a line that appears to do work always does.

The mirror image is also enforced. A telling operation cannot be used inside an expression, because
it has nothing to contribute to one.

## Almost every line is a telling

Here is the part that surprises people who have written other languages.

<!--INCLUDE
from: "../../bubas-doc/src/test/resources/programs/approve-expense.bu"
start:
  pattern: 'DECLARE total'
  include: true
end:
  pattern: 'total = TOTAL_OF'
  include: false
prefix: "```"
postfix: "```"
margin: 0
_content_generated_: 32:md5:0daccc3432868d92d862bd16dd6b38c6
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```
DECLARE total DECIMAL

```
<!--/INCLUDE-->

`DECLARE` is not syntax. It is a telling operation like any other, supplied by a standard module
that almost every BUBAS language installs, and an application that wanted to spell declarations
differently would simply not install it. The same is true of assignment: `total = TOTAL_OF(claim)`
is an operation whose shape happens to include an equals sign in the middle.

Nothing in the language privileges either of them, and nothing in the runtime treats them specially.
They are shipped because a language without a way to declare and assign is unusable, not because
they are part of what BUBAS *is*.

That has a practical consequence. Telling operations can have keywords in the middle of them, not
just at the front — which is how `DECLARE total DECIMAL` can name a thing and its type in one line
without commas, and how a vocabulary can offer `SEND claim TO "finance"` if that reads better than
`SEND claim, "finance"`. Part 3 is where those shapes get designed; here it is enough to know that a
line with words scattered through it is still one operation, and that the words are part of its
name.

## How to tell them apart

Three questions, in order:

**Is it part of a larger expression?** Then it is asking. Only an answer can be assigned, compared,
added to something, or returned.

**Is it a whole line on its own?** Then it is telling. It may or may not have brackets, and any
keywords among its arguments belong to it.

**Does its name appear in the vocabulary?** If not, it is not an operation at all, and the program
will not compile — which is the subject of chapter 8 and, in a different sense, of chapter 11.

## Where the words come from

None of the operations in this chapter are part of BUBAS. `TOTAL_OF`, `APPROVE`, `REJECT` and
`NOTE` exist because somebody building this application decided that this domain asks those
questions and issues those instructions.

Which raises the obvious question: what happens when the rule you have been asked to write needs a
question nobody thought to provide? That is a normal situation with a normal answer, and chapter 11
is about it.

The next chapter deals with the one kind of value this book has kept deferring: the domain objects
themselves, which a program can hold but never open.
