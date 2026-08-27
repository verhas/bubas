# Why I Don't Want an LLM Generating Java Business Logic

A pull request arrives. A few hundred lines of Java implementing the new discount rule: tiered
thresholds, a regional exception, something about loyalty tiers that nobody can quite explain. It
compiles. The tests pass. An LLM wrote it in about forty seconds.

Now: who reviews it?

The person who owns that rule is in commercial operations. She knows exactly which customers should
get the discount and why the regional exception exists, and she cannot read Java. The person who can
read Java has no idea whether the thresholds are right. He will check that the code looks
reasonable, because that is the only thing he is equipped to check.

So the review that happens is not the review that matters. That is the problem I keep coming back
to, and it has nothing to do with how good the model is.

## This is not an argument about whether the model is good enough

Most objections to generated code are about competence. The model hallucinates an API. It gets an
edge case wrong. It writes something that works on the happy path and falls over in production.

I find these arguments unconvincing, because they expire. Models get better. Any position resting on
today's error rate is a position with a shelf life, and people who staked one out three years ago
have mostly had to retreat from it.

The durable question is different. It is not how well the model writes. It is **what the thing it
writes is permitted to say.**

A model that never makes a mistake, handed Java, can still emit `Runtime.getRuntime().exec(...)`. Not
because it is malicious or confused — because that sentence is available in the language it was
asked to write. Competence and authority are separate axes, and improving the first does nothing to
the second.

## "Write it in Java" is a much bigger grant than anyone means

Consider what you actually authorise when you ask for a discount rule in Java.

You authorise file system access. Network sockets. Reflection. Thread creation. Process execution.
Every class on the classpath, including the ones that talk to your database, your payment provider,
and your secrets manager. You authorise the loading of new code at runtime.

Nobody intends to grant any of this. It arrives free with the language, the way a house key also
opens the shed. The task needed perhaps six operations — look up an order, total it, check a
customer's tier, apply a discount, log the decision, approve or refuse — and the language you handed
over contains everything Java contains.

That gap, between the authority the task requires and the authority the language confers, is the
whole of it. It exists whether or not the model is trustworthy. It exists whether or not anyone acts
on it. It is just very large, and it is not visible in the pull request.

## The usual guardrails are denial-lists

The standard responses all share a shape.

Tell the model in the prompt not to touch the file system. Review the generated code. Run static
analysis and flag dangerous calls. Run it in a sandbox with a restricted security policy.

Every one of these asks you to enumerate what must not happen, over a space of things that can
happen which is effectively unbounded. You are writing a deny-list against a general-purpose
language. You have to think of `exec`. Then of reflection reaching `exec`. Then of the dependency
that shells out on your behalf. Then of the next one.

We learned this lesson in security a long time ago and reached a settled answer: allow-lists beat
deny-lists, because the allow-list is finite and you wrote it. Somehow, when the subject is
generated code, we reach for the deny-list again.

## Shrink the language, not the model

The alternative is to stop constraining a powerful language and instead supply a small one.

Give the model a vocabulary that contains exactly the operations the domain has — the six from
earlier, say — and nothing else. Not a restricted Java. A different, much smaller language, whose
entire vocabulary is a list your team wrote in advance, in Java, on purpose.

Generated business logic then looks like this:

```
PROGRAM ApproveOrder(orderId INTEGER, limit DECIMAL) RETURNS BOOLEAN
    DECLARE purchase Order
    DECLARE total DECIMAL

    purchase = LOAD_ORDER(orderId)
    total = ORDER_TOTAL(purchase)

    IF total > limit THEN
        REJECT purchase, "over limit"
        RETURN FALSE
    END IF

    APPROVE purchase
    RETURN TRUE
END.
```

`LOAD_ORDER`, `ORDER_TOTAL`, `REJECT` and `APPROVE` are not part of the language. They are Java
classes somebody decided to expose. `Order` is a Java object the program can hold and pass and never
look inside — there is no `purchase.customer.account.balance` here, only the operations the domain
chose to have.

Two things change, and the second matters more than the first.

The obvious one: dangerous programs are no longer forbidden, they are **inexpressible**. If the
model emits `DELETE_ALL_ORDERS`, nothing rejects it on policy grounds. The name means nothing. The
program does not compile, for the same reason a typo does not compile. There is no deny-list because
there is nothing to deny.

The less obvious one: the commercial operations manager can read the program above. She can tell you
whether the threshold is right, whether the rejection reason is the one the contract requires,
whether an approval should have been logged. The review moves to the person who owns the rule. That
is the review that was missing at the start of this article, and no amount of static analysis over
generated Java produces it.

A small language buys something else, quietly. With no data structures, one global scope, no null,
and a compiler that refuses to run a program which reads a variable before it is set, entire
families of subtle wrongness have nowhere to live. Not caught — absent.

## What it costs, and what it does not buy

I would not trust this argument from someone who only listed the advantages, so here are the bills.

**You have to design the vocabulary.** Somebody sits down and decides that the domain has
`ORDER_TOTAL` and `CUSTOMER_RISK` and not forty other things. That is real work, done before the
first generated line, by someone who understands the domain. And if nobody on your team can write
that list, this approach will not help you. It will only show you that the list does not exist. That
is worth finding out, but it is not a pleasant morning.

**Complex algorithms stay in Java.** Business rules are algorithms too, and they belong in the small
language; that is the point. But route optimisation, a scoring model, anything with real
computational substance belongs behind a function the small language calls. The signal is usually
that you want to build up a data structure, or that you want a helper you can call from three
places. Both mean you have wandered out of business logic and should walk back.

**The boundary bounds naming, not doing.** This is the limit people miss, and overstating it is how
the idea gets dismissed. A function you expose can do anything Java can do. `RUN_SHELL_COMMAND` is a
perfectly registrable operation. The vocabulary is only as narrow as the operations you chose, and
choosing them badly gets you exactly the exposure you were avoiding.

**There are no resource limits yet.** A generated program can still loop forever. This one is a gap
rather than a decision: the interpreter walks the program one statement at a time, so a step budget
or a deadline is a small addition rather than a redesign, and it will go in when somebody needs it.
Until then, untrusted input needs the same containment any untrusted workload needs.

What you get is narrower than "safe" and more useful than it sounds: the set of things a generated
program can name is finite, written down, and reviewable by a human before anything is generated at
all.

## When I would still write Java

If the thing is genuinely computational, write Java. If it is a one-off that will be deleted next
week, use whatever is nearest — Java, Python, a shell script — and let the model write it; do not
build a vocabulary for something with a life expectancy of days. If the rules change so fast that
the vocabulary would be obsolete before it settled, the overhead will not pay for itself.

And if your business logic is already reviewed by people who can read it, understand it, and are
accountable for it being right — you may not have the problem this solves. Plenty of teams do not.

But if you are about to let a model write business rules in Java, ask the question I started with,
because the answer is usually uncomfortable. Somebody is going to approve that pull request. Are
they the person who knows whether the rule is correct?

If not, the language is too big.

---

*I have been building a small language along these lines: [BUBAS](https://github.com/verhas/bubas),
an orchestration language for subject-matter experts, embedded in Java. The example above is real
BUBAS. The idea does not require my implementation, though — the argument is about the size of the
language you hand over, and you can shrink yours however you like.*
