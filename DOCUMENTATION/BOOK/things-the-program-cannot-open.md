# Things the program cannot open

<!-- abstract -->
Some values a program holds are sealed: it can be given one, pass it on, and ask questions about
it, but never reach inside. There is no `claim.employee.manager.email`, and this chapter argues
that the missing dot is a feature. What it means for you as a rule writer, and what to do when the
question you want to ask has no operation yet.
<!-- /abstract -->

---

## The fifth kind of value

Chapter 3 listed four kinds of value and then admitted there was a fifth. This is it.

`claim` is a `Report`. It is not a number, or text, or a yes-or-no; it is the expense claim itself,
the same object the surrounding application was holding when it decided to run this rule. The
program receives it, works with it, and hands it to operations that know what to do with it.

What the program cannot do is look inside.

That is not a restriction bolted on afterwards. There is no syntax in BUBAS for reaching into
anything — no dot, no square brackets on a domain value, no field names, nothing. The capability is
absent rather than forbidden, which is a distinction this book keeps returning to.

## What you can do with one

Rather more than you might expect from a value you cannot open.

You can receive one as a parameter, which is how `claim` arrives. You can declare a variable of
that type and assign to it. You can pass one to any operation that takes it, and you can pass the
same one to several. An operation can hand you a new one, and you can keep it.

In other words a domain value behaves like any other value, in every respect except that its
contents are somebody else's business. It is a thing you hold and give to people who know what to
do with it, like a sealed envelope with a name on the front.

## What you cannot do

Three refusals, and each one says something different.

**You cannot reach inside.**

<!--INCLUDE
from: "../../bubas-doc/target/doc-outputs/stage1-reaching-inside.txt"
prefix: "```"
postfix: "```"
_content_generated_: 68:md5:e5b1c35d9203a1ca2df4ec887d59cf85
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```
line 3: unknown statement who
        who = claim.employee
```
<!--/INCLUDE-->

That message is worth explaining, because it is the one you will meet if you try, and it does not
mention dots. It cannot: there is no member-access syntax in the language for the compiler to
complain about, so `claim.employee` is not a bad expression, it is not an expression. The line
matches no statement the language knows, and that is what it reports. If you get this message, the
question to ask is not "what is wrong with my dot" but "what operation gives me the employee".

**You cannot turn one into text.**

<!--INCLUDE
from: "../../bubas-doc/target/doc-outputs/stage1-rendering.txt"
prefix: "```"
postfix: "```"
_content_generated_: 184:md5:334ecb53df482d1c02c7bbb9cc89e712
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```
line 2: Report has no text form and cannot be added to a STRING; the embedder can expose a domain-named function for it
        NOTE "deciding " + claim + " against " + limit
```
<!--/INCLUDE-->

A number knows how to render itself and so does a boolean. A claim does not, because BUBAS has no
idea what a claim is. If a message needs to identify the claim — and messages usually do — that is
an operation somebody provides, named in domain terms, deciding for itself whether a claim reads as
its number, its claimant, or both.

**You cannot compare two of them.**

<!--INCLUDE
from: "../../bubas-doc/target/doc-outputs/stage1-comparing.txt"
prefix: "```"
postfix: "```"
_content_generated_: 174:md5:746a00c4ca1903157d3c9c7836e0c9fa
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
```
line 3: Report and Report cannot be compared; an opaque value is a black box, so the embedder decides what comparing two of them means
        IF claim = other THEN
```
<!--/INCLUDE-->

Not even for equality. This surprises people more than the other two, and the message explains
itself: comparing two claims is a domain question, not a language one. Are two claims equal when
they have the same number? The same claimant and dates? Whoever built the vocabulary knows; BUBAS
does not, and declines to guess. If your rule needs it, there is an operation for it or there needs
to be.

## Why it is total

The obvious middle path would be to expose *some* of a domain value. Let a rule read a claim's
number and its date, say, but not go wandering through the object graph. Most systems that try this
end up there.

BUBAS does not, for a reason that only becomes visible when you think about who reads the rules.

If a rule could see fields, then what a rule can know would be determined by the shape of a Java
class — and Java classes grow. Someone adds a field for an unrelated feature, and the vocabulary
has silently gained a word nobody decided to add, nobody documented, and nobody reviewed. The list
of things a rule can find out would stop being a list anybody wrote.

With total opacity, that list is exactly the set of operations somebody chose to expose. It can be
printed. It can be reviewed by the finance manager before a single rule is written. It changes only
when a person changes it deliberately. Chapter 11 is about reading that list, and it only works as
a complete account of the language because there is no second, accidental way in.

There is a second benefit, which Part 2 collects. A value nobody can open is a value nobody needs
to *construct* in order to test a rule — a test can name one instead of building one. Total opacity
is what makes that possible, and partial opacity would not.

## The missing dot

It is worth sitting with what is absent, because the absence is the point.

`claim.employee.manager.email` cannot be written. Neither can the version of that expression which
works fine for eleven months and then meets a claimant whose manager has left. Neither can the rule
that quietly depends on a field somebody is about to rename, nor the one that reaches three objects
deep into a structure whose shape was never meant to be a public interface.

Every one of those is a normal thing to write in a general-purpose language, and every one of them
is a way for a business rule to acquire a dependency that its author did not think about and its
reviewer cannot see. None of them are available here. What a rule depends on is the operations it
calls, and those are on the page.

## When you need something you cannot get

Sooner or later — usually sooner — you will want to ask a question the vocabulary does not have.
The claim's submission date. Whether the claimant is a contractor. How many days the trip covered.

This is not a wall, and it is not a sign that you are using the language wrongly. It is the normal
way a BUBAS vocabulary grows: somebody wants to write a rule, the rule needs a question, the
question gets added. Chapter 11 covers how to find out what exists and how to ask for what does
not, and Part 3 is where the person who builds it does their work.

What you should *not* do is work around it. If you find yourself computing something from three
other operations because the direct question is missing, you have moved a piece of domain knowledge
out of the vocabulary and into a rule, where the next person to write a similar rule will not find
it. Ask for the operation.

## What is coming

You now know all five kinds of value and both kinds of operation, which is the whole of what a
BUBAS program is made of.

The next three chapters are about putting them together: deciding between branches, repeating work
across the lines of a claim, and then — the one that changes how the rest of the book reads — what
the compiler refuses to accept and why the list is shorter than you would expect.
