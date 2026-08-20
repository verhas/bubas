# BUBAS

**An orchestration language for subject matter experts.**

BUBAS lets the person who understands the business write the business process, while developers
keep the algorithms, the domain objects and the infrastructure in Java. It is deliberately small:
no functions, no data structures, no objects, one global scope. Everything a script can do beyond
sequencing, deciding and looping comes from a vocabulary that Java developers define for it.

```basic
PROGRAM ApproveOrder(orderId INTEGER, limit DECIMAL) RETURNS BOOLEAN
    DECLARE order Order
    DECLARE total DECIMAL
    DECLARE taxRate DECIMAL FINAL = 0.07

    LET order = LOAD_ORDER(orderId)

    IF NOT ORDER_WAS_FOUND(order) THEN
        LOG_EVENT "ERROR", "no such order: " + orderId
        RETURN FALSE
    END IF

    LET total = ORDER_TOTAL(order) * (1.0 + taxRate)

    IF total > limit THEN
        LOG_EVENT "INFO", "over limit: " + total
        RETURN FALSE
    END IF

    RETURN TRUE
END.
```

## Why it looks like this

**Typed, and strict about it.** Every type error, every variable read before it holds a value,
every unreachable statement and every unused variable is reported before the script runs. A
business process that fails halfway through is expensive; failing at compile time is not.

**Extensible by the embedder, not by the script.** A Java developer registers functions and
statement patterns. The script author sees a vocabulary shaped for their domain — `VALIDATE order
AGAINST rules`, not a general-purpose language they must first learn.

**Built for generated code.** The vocabulary is fixed at startup and exportable, the grammar is
line-based, and the analyser rejects the mistakes an LLM actually makes. A generated script either
compiles or comes back with a precise diagnostic.

**Opaque by design.** Domain objects cross into BUBAS as registered opaque types. A script can
hold one, pass it and store it in an array, but never inspect it. Anything requiring
interpretation — comparing two, rendering one as text, testing one for absence — is a function the
embedder supplies, named for the domain.

## Quickstart

```java
BubasLanguage lang = BubasLanguage.builder()
    .defineOpaqueType("Order", Order.class)
    .defineFunction("LOAD_ORDER")
        .parameter("orderId", BubasType.INTEGER)
        .returns(BubasType.opaque("Order"))
        .as(ctx -> orderService.load(ctx.argument("orderId").asLong()))
    .registerService(OrderService.class, orderService)
    .seal();

BubasProgram prog = lang.compile(source);

boolean approved = prog.newInterpreter()
    .argument("orderId", 42L)
    .argument("limit", new BigDecimal("1000.00"))
    .run()
    .asBoolean();
```

Three objects, three lifetimes. A **Language** is sealed once and shared by everything. A
**Program** is compiled once and reused. An **Interpreter** is cheap, runs once, and carries
whatever varies per run.

## Documentation

- [`SPEC.md`](SPEC.md) — the language definition and Java API reference
- [`CLAUDE.md`](CLAUDE.md) — conventions for working in this repository

## Status

**Design complete, implementation not started.** The specification is settled; no code exists yet.

| Phase | Scope | State |
|-------|-------|-------|
| 1 | Lexer, parser, AST, symbol table, analyser, interpreter, function registry | not started |
| 2 | Pattern system, custom statements, optional prelude packages | not started |
| 3 | Code generation to Java, plus the interpreter/codegen conformance suite | not started |
| 4 | Vocabulary export and prompt tooling for LLM code generation | not started |
| 5 | Diagnostics, profiling, hardening | not started |

Phase 1 delivers a usable interpreter on its own. Phases 2 and 4 are what make BUBAS worth
embedding; phase 3 is an optimisation and can be deferred without affecting semantics.

Five items remain deliberately open and are listed in [SPEC.md §16](SPEC.md#16-open-questions).
