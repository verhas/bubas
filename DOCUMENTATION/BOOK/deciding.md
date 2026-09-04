# Deciding

<!-- abstract -->
`IF`, `ELSEIF`, `ELSE`, comparison, and `AND` / `OR` / `NOT` written as the words they are. How the
order of tests is itself part of the policy — a claim caught by the meals cap before the total is
ever considered is a decision somebody made, and it should be one they made on purpose.
<!-- /abstract -->

---

## One test

The simplest decision has one test and one thing to do about it:

<!--INCLUDE
from: "../../bubas-doc/src/test/resources/programs/approve-expense.bu"
start:
  pattern: 'IF total > limit'
  include: true
end:
  pattern: 'END IF'
  include: true
prefix: "```"
postfix: "```"
margin: 0
_content_generated_: 104:md5:5c533d8fdbca110852001d9f1c573bcf
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```
IF total > limit THEN
    REJECT claim, "over the " + limit + " limit"
    RETURN FALSE
END IF
```
<!--/INCLUDE-->

`IF`, a condition, `THEN`, some statements, `END IF`. The block is closed with words rather than
with indentation or brackets, so a rule cannot change meaning because somebody's editor retabbed
it.

The condition has to be a `BOOLEAN`. There are no truthy numbers here and no empty string standing
in for false: a condition either answers yes or no, or the program does not compile.

## More than two ways out

Real policy rarely divides in two. A claim can be fine, or need a manager, or be so far out that no
manager should be signing it off. That is `ELSEIF`:

<!--INCLUDE
from: "../../bubas-doc/src/test/resources/programs/escalating-expense.bu"
start:
  pattern: 'IF total > ceiling'
  include: true
end:
  pattern: 'END IF'
  include: true
prefix: "```"
postfix: "```"
margin: 0
_content_generated_: 222:md5:7d05b11447eba638d4c7c82a70dade6a
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```
IF total > ceiling THEN
    REJECT claim, "over " + ceiling + " — needs a business case first"
    RETURN FALSE
ELSEIF total > limit THEN
    ESCALATE claim, "over the " + limit + " limit"
    RETURN FALSE
END IF
```
<!--/INCLUDE-->

`ELSEIF` is one word. `ELSE IF`, `ELIF` and `ELSIF` are all rejected — the first of those because
BUBAS matches statements a whole line at a time, so `ELSE IF total > limit THEN` is two statements
crowded onto one line and fails without needing a rule of its own.

The tests are tried in order and the first one that holds wins. Everything after it is skipped,
including tests that would also have been true.

## An `ELSE` for everything left

`ELSE` catches whatever reached the end without matching, and here is a rule using the full chain,
with one extra input: whether the claim was marked urgent.

<!--INCLUDE
from: "../../bubas-doc/src/test/resources/programs/urgent-expense.bu"
start:
  pattern: 'IF total > ceiling'
  include: true
end:
  pattern: 'END IF'
  include: true
prefix: "```"
postfix: "```"
margin: 0
_content_generated_: 381:md5:78aafd55b8067bc8ba6b2968ef162810
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```
IF total > ceiling THEN
    REJECT claim, "over " + ceiling + " — needs a business case first"
    RETURN FALSE
ELSEIF total > limit AND NOT urgent THEN
    ESCALATE claim, "over the " + limit + " limit"
    RETURN FALSE
ELSEIF total > limit THEN
    NOTE "urgent, so cleared over the limit"
    APPROVE claim
    RETURN TRUE
ELSE
    APPROVE claim
    RETURN TRUE
END IF
```
<!--/INCLUDE-->

Four ways out, and every one of them returns. Running it against three claims, with the middle one
tried twice — once ordinary, once urgent:

<!--INCLUDE
from: "../../bubas-doc/target/doc-outputs/stage2-branches.txt"
prefix: "```"
postfix: "```"
_content_generated_: 590:md5:e9e33283b520991caec4075d033dee70
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```
RouteExpense(claim = report 1 (Alice), limit = 200.00, urgent = FALSE)
    approved report 1 (Alice) for 128.40
    => TRUE

RouteExpense(claim = report 2 (Erin), limit = 200.00, urgent = FALSE)
    escalated report 2 (Erin) — over the 200.00 limit
    => FALSE

RouteExpense(claim = report 2 (Erin), limit = 200.00, urgent = TRUE)
    urgent, so cleared over the limit
    approved report 2 (Erin) for 450.00
    => TRUE

RouteExpense(claim = report 3 (Frank), limit = 200.00, urgent = TRUE)
    rejected report 3 (Frank) — over 1000.00 — needs a business case first
    => FALSE
```
<!--/INCLUDE-->

Erin's claim is the same claim, at the same limit, both times. One `BOOLEAN` sends it to a manager
or clears it.

## Combining tests

Three words join and negate conditions, and they are words rather than symbols because a rule is
meant to be read aloud:

- `AND` — both must hold
- `OR` — either will do
- `NOT` — the opposite

`total > limit AND NOT urgent` groups the way it reads. `NOT` binds tightest, then the comparisons,
then `AND`, then `OR`. So `a > b AND c > d` compares first and combines second, which is what
anybody would assume; and `a OR b AND c` means `a OR (b AND c)`, which is the one place people's
assumptions differ, so bracket it if there is any doubt.

Brackets work as you expect and cost nothing. In a rule that somebody in finance has to approve,
they are usually worth adding even where the precedence is on your side.

## Comparing

Six comparisons: `=`, `<>`, `<`, `>`, `<=`, `>=`.

Note the first two. Equality is a single `=` — there is no `==` in this language — and "not equal"
is `<>` rather than `!=`. Both are how people wrote them before programming borrowed the symbols
from mathematics, and both read better to somebody who does not write code for a living.

Numbers compare against numbers, whole or decimal, mixed freely. Text compares too, character by
character, so `<` on two strings asks which comes first alphabetically. Booleans can only be tested
with `=` and `<>`, because asking whether `TRUE` exceeds `FALSE` is not a question. Domain values
cannot be compared at all, for the reasons chapter 5 gave.

## The order of the tests is the policy

This is the part worth slowing down for.

A chain of tests is not a set of independent rules. It is an ordered list, and moving one line
changes what the rule does. In the stage-3 program you will meet in the next chapter, the meals cap
is checked before the total, which means a claim of 75.00 against a limit of 200.00 can still fail
— it never reaches the total test, because the meals test caught it first.

Is that right? It depends entirely on what the policy is meant to say, and that is not a question
for whoever typed the rule. It is a question for whoever owns it, which is the whole reason this
language exists.

So when reviewing a rule, read the tests in order and ask at each one: *if this fires, should the
rest still be considered?* Most of the arguments worth having about a business rule turn out to be
arguments about ordering, and here the ordering is on the page rather than distributed across four
methods.

## A test has to be a question

One thing an `IF` may not do is ask something the compiler can already answer.

`IF 1 = 1 THEN` is the obvious case and nobody writes it. The case people do write is a test on a
value the rule worked out a few lines earlier — a flag set at the top, a cap assigned from a figure
in the source. The compiler follows those values, so it knows the answer, so it refuses the test:
one of the two branches cannot run, and a branch that cannot run is a mistake rather than a
formality.

The remedy is nearly always the same, and it is worth knowing now rather than at the diagnostic. A
value the rule sets for itself is decided; a value the rule is *given* is not. Anything the rule's
behaviour should turn on belongs in the program's parameters, where the application supplies it and
a test can set it either way. Chapter 8 works through the shape this takes in practice.

## What is coming

One test at a time is enough for a claim considered as a single total. The next chapter opens the
claim up and works through its lines one at a time, which needs a way of repeating — and brings
back a second kind of value you cannot look inside.
