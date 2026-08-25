# BUBAS

**An orchestration language for subject matter experts, embedded in Java.**

BUBAS lets the person who understands the business write the business process, while developers
keep the algorithms, the domain objects and the infrastructure in Java. It is deliberately small:
no user-defined functions, no data structures, no objects, one global scope. Everything a script
can do beyond sequencing, deciding and looping comes from a vocabulary that Java developers define
for it.

```basic
PROGRAM ApproveOrder(orderId INTEGER, limit DECIMAL) RETURNS BOOLEAN
    DECLARE purchase Order
    DECLARE total DECIMAL
    DECLARE taxRate DECIMAL FINAL = 0.07

    purchase = LOAD_ORDER(orderId)

    IF NOT ORDER_WAS_FOUND(purchase) THEN
        LOG_EVENT "ERROR", "no such order: " + orderId
        RETURN FALSE
    END IF

    total = ORDER_TOTAL(purchase) * (1.0 + taxRate)

    IF total > limit THEN
        LOG_EVENT "INFO", "over limit: " + total
        RETURN FALSE
    END IF

    RETURN TRUE
END.
```

`LOAD_ORDER`, `ORDER_WAS_FOUND`, `ORDER_TOTAL` and `LOG_EVENT` are not part of BUBAS. They are
Java classes the host application chose to expose. Neither is `Order` — it is a Java type the
script may hold and pass but never look inside.

## Why it looks like this

**BUBAS is not BASIC.** The name is a nod, not a lineage. There is no `LET`, no line numbers, no
`GOTO`, no user-defined subroutines — and no general-purpose language hiding under the syntax.
What survives is the one thing BASIC got right: a reader who is not a hard-core programmer can
follow it.

**Typed, and strict about it.** Every type error, every variable read before it holds a value,
every unreachable statement and every unused variable is reported before the script runs. A
business process that fails halfway through is expensive; failing at compile time is not.

**Extensible by the embedder, not by the script.** A Java developer registers functions and
statement patterns. The script author sees a vocabulary shaped for their domain — `VALIDATE order
AGAINST rules`, not a general-purpose language they must first learn.

**Built for generated code.** The vocabulary is fixed at startup, the grammar is line-based, and
the analyser rejects the mistakes an LLM actually makes. A generated script either compiles or
comes back with a precise diagnostic naming the line.

**Opaque by design.** Domain objects cross into BUBAS as registered opaque types. A script can
hold one, pass it and store it in an array, but never inspect it. Anything requiring
interpretation — comparing two, rendering one as text, testing one for absence — is a function the
embedder supplies, named for the domain. There is no `order.customer.account.balance` in BUBAS,
only `CUSTOMER_OF(purchase)` if the embedder decided that operation should exist.

## The vocabulary is the language

BUBAS itself stays the same size for every embedder. What changes is what you register. An
order-processing application might expose:

```text
LOAD_ORDER   ORDER_TOTAL   CUSTOMER_OF   CUSTOMER_RISK
APPROVE      REJECT        REQUEST_APPROVAL
```

an insurance system:

```text
LOAD_CLAIM   POLICY_OF   CLAIM_AMOUNT   CALCULATE_EXPOSURE
APPROVE_CLAIM   REQUEST_DOCUMENTS
```

and the resulting script reads as that domain rather than as a program that happens to be about it:

```basic
PROGRAM RouteClaim(claimId INTEGER) RETURNS BOOLEAN
    DECLARE filing Claim
    DECLARE exposure DECIMAL

    filing = LOAD_CLAIM(claimId)
    exposure = CALCULATE_EXPOSURE(filing)

    IF exposure > 50000.0 THEN
        REQUEST_DOCUMENTS filing, "loss adjuster report"
        RETURN FALSE
    END IF

    APPROVE_CLAIM filing
    RETURN TRUE
END.
```

You get a domain-specific language without writing one. The grammar, the type system, the
analyser and the diagnostics are BUBAS's; only the words are yours.

Note `filing` rather than `claim`: a registered type name is reserved, so a variable may not be
named after its type. It is the same rule that stops `userId` and `UserID` from being two
variables, and it surprises people, so the diagnostic names the type explicitly.

## What a script cannot reach

A BUBAS script can name exactly three things: its own variables, the language's own operators and
control flow, and the vocabulary the embedder registered. There is no import, no reflection, no
`eval`, no way to name a Java class, and no filesystem, process or network primitive built into
the language. If the host did not expose an operation, a script cannot express it — the program
does not fail at run time, it fails to compile, because the name means nothing.

That makes the registered vocabulary an explicit boundary, which is worth being precise about:

- **It bounds what a script can name, not what your code can do.** A function you expose can do
  anything Java can. The boundary is only as narrow as the operations you decide to register, and
  `RUN_SHELL_COMMAND` is a perfectly registrable function.
- **Registration is opt-in.** Discovery mechanisms find whatever is on the classpath; the builder
  decides what gets registered, so an unrelated jar cannot widen the vocabulary — or reserve a word
  an existing script uses as a variable.
- **It is not a resource sandbox.** BUBAS has no execution time or memory limits. `DO WHILE TRUE`
  compiles and runs forever. Untrusted input needs the same containment any other untrusted
  workload needs.

Within those limits it does answer one specific question well: how much of your application are
you willing to let generated code program? A script whose entire vocabulary is
`LOAD_ORDER, ORDER_TOTAL, APPROVE, REQUEST_APPROVAL` cannot be talked into doing anything else,
and the failure mode for trying is a compile error rather than a judgement call at run time.

## Quickstart

A host application builds the language it wants to expose, seals it, and compiles against it:

```java
BubasLanguage lang = BubasLanguage.builder()
    .defineOpaqueType("Order", Order.class)
    .defineFunction("LOAD_ORDER", LoadOrder.class)
    .defineFunction("ORDER_TOTAL", OrderTotal.class)
    .defineStatement("APPROVE {expression/Order:target}", ApproveOrder.class)
    .seal();

BubasProgram prog = lang.compile(source);

boolean approved = Interpreter.of(prog)
    .argument("orderId", 42L)
    .argument("limit", new BigDecimal("1000.00"))
    .registerService(OrderService.class, orderService)
    .run()
    .asBoolean();
```

```java
public final class LoadOrder {
    public Order call(Context ctx, long orderId) {
        return ctx.service(OrderService.class).load(orderId);
    }
}
```

The signature is read off the Java method: `LOAD_ORDER(orderId INTEGER) -> Order`. Nothing is
declared twice, and nothing names a method in a string — a class reference is what an IDE renames
and the compiler checks.

A statement is the same idea with a shape. The pattern `APPROVE {expression/Order:target}` both
defines the syntax and reserves `APPROVE`, and the handler's parameters must line up with the
placeholders — registering `"APPROVE"` for a handler that takes an argument is rejected when the
language is sealed, not when a script first runs.

Three objects, three lifetimes. A **Language** is sealed once and shared by everything. A
**Program** is compiled once and reused. An **Interpreter** is cheap, single-use, and carries
whatever varies per run — arguments, services, `MathContext`, logger.

One class is one function or one command. That is what lets the signature be derived rather than
declared, and it means compiled output can call the implementation directly instead of dispatching
by string key through a registry that would have to be rebuilt first. The runtime constructs the
class itself, with no arguments, so it cannot capture the embedder's objects — every dependency
arrives through `ctx.service(...)`, which makes the service registry the only route from a shared,
sealed language to per-run state.

Arrays cross the boundary as native Java arrays — `long[]`, `BigDecimal[]`, `Order[]` — passed as
the interpreter's own storage, so `Arrays.sort` works and an in-place reorder is visible to the
script.

## How it fits together

```text
source ──▶ lexer ──▶ pattern matcher ──▶ parser ──▶ symbol table
                                                        │
                        interpreter ◀── core tree ◀── flow analysis + lowering
```

| Module | Contents |
|--------|----------|
| `bubas-api` | types, values, the embedder-facing interfaces |
| `bubas-lexer` | tokens, logical lines, continuation and comment handling |
| `bubas-analyser` | `BubasLanguage`, `BubasProgram`, patterns, parser, analysis, lowering |
| `bubas-runtime` | `Interpreter` and dispatch |
| `bubas-support` | the standard statements and functions |
| `bubas-test` | executable `.bu` programs and the runner that checks them |

Lowering produces a typed core tree that the interpreter executes. It exists so that a future code
generator consumes the same tree rather than re-deriving semantics from the AST — divergence
between backends is the characteristic bug of that design, and a shared IR is what prevents it.

## Try it

```bash
git clone https://github.com/verhas/bubas.git
cd bubas
mvn test
```

The most readable place to see what the language does today is the script corpus:

```text
bubas-test/src/test/resources/scripts
```

Each `.bu` file is a whole program that declares its own expectation in a header comment — that it
should not compile, that it should fail at run time with a given message and line, or that it
should run to completion. The `ok/` scripts check their own results with `ASSERT`, so a script that
finishes has verified itself rather than merely not crashed.

## Status

**Implemented and executable.** The front end, analyser and interpreter all work; `mvn test` runs
405 tests, 63 of which are whole BUBAS programs executed end to end.

| Phase | Scope | State |
|-------|-------|-------|
| 1 | Lexer, parser, symbol table, flow analysis, lowering, interpreter | done |
| 2 | Pattern system, custom statements, standard statement and function set | done |
| 3 | Code generation to Java, plus the interpreter/codegen conformance suite | not started |
| 4 | Vocabulary export and prompt tooling for LLM code generation | not started |
| 5 | Diagnostics, profiling, hardening | ongoing |

Not built yet, beyond the phase table: extension discovery. `SPEC.md` describes a builder API for
registering functions and commands found on the classpath by `ServiceLoader`; today registration is
explicit, one `defineFunction` or `defineStatement` at a time. The `provider()` static-factory
convention implementations may use is already supported, so classes written for discovery work
through explicit registration unchanged.

This is an MVP, not a released library. Expect the API to move.

Six language questions remain deliberately open and are listed in
[SPEC.md §16](SPEC.md#16-open-questions).

## Documentation

- [`SPEC.md`](SPEC.md) — the language definition and Java API reference; normative
- [`CLAUDE.md`](CLAUDE.md) — conventions for working in this repository

## Contributing

Issues, experiments, criticism and pull requests are welcome. The most useful reports are the ones
that test the design rather than the code:

- programs that should be valid but are rejected
- programs that should be rejected but are accepted
- diagnostics that do not explain themselves
- real embedding attempts, especially the ones where the capability model got in the way
- BUBAS generated by an LLM, and what it got wrong

If you embed BUBAS in something real, open an issue and say what happened.

## License

Apache License 2.0 — see [LICENSE](LICENSE).

Copyright 2026 Peter Verhas
