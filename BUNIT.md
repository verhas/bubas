# BUNIT — unit testing BUBAS programs

**Design notes, not a specification.** Nothing here is built. Decisions that are settled are marked
as such; everything else is an argument in progress. When BUNIT is implemented, the settled parts
move into `SPEC.md` and this file keeps only what is still open.

Syntax shown as *verified* was compiled against the current analyser; the rest is sketch.

## What it is

A BUBAS program orchestrates a vocabulary the host application supplies. BUNIT tests that
orchestration — the decisions, the branches, the sequencing — by replacing the vocabulary with
mocks and asserting on what the program did with it.

A test is itself a BUBAS program, so the person who writes the business rule can write the test for
it. That is the whole point: an embedder ships a REST, MCP or CLI front end, and a subject matter
expert writes and runs tests without a Java toolchain.

## Non-goals

BUNIT does not test opaque types, Java function implementations, or command implementations. Those
are Java, and Java has JUnit. BUNIT tests BUBAS source and nothing else.

It follows that a green BUNIT suite says the orchestration is right, not that the system works. A
mock that says `LOAD_ORDER` returns an order for id 42 is not evidence that it does. This is the
ordinary limit of mock-based testing and it is accepted deliberately, not overlooked — see
[Running against the real vocabulary](#running-against-the-real-vocabulary).

## Why BUBAS is unusually mockable

A script may hold an opaque value, pass it, and store it in an array. It may never inspect one.
Every operation that would reveal anything about an `Order` — comparing two, rendering one,
testing one for absence — is a function the embedder supplies, and in a test that function is
mocked too.

So a mock never has to produce a real `Order`. It produces an **uninterpreted token**: a value with
identity and nothing else. The program under test cannot distinguish it from the real thing,
because the language gives it no operation that would tell them apart. Total opacity, which exists
for encapsulation, buys total mockability for free.

This is what makes "tests written in the DSL" viable here when it usually is not. The question that
normally sinks it — how does a language that cannot construct anything construct a domain object? —
does not arise. It never needs to.

## Architecture: interception at dispatch

**Settled.** Mocking substitutes behaviour at call dispatch. The program under test compiles
against the **real** language; the interpreter consults a mock table before invoking an
implementation.

Two alternatives were considered and rejected:

*Compile the subject against a parallel mocked language.* Signatures are derived from the Java
method ([SPEC §10.2](SPEC.md#102-signatures-are-derived-from-java)), so each mock needs a class
whose Java signature matches the function it replaces — `Order call(Context, long)` for one,
`BigDecimal call(Context, Order)` for the next. One generic mock class cannot express both, so this
needs either bytecode generation per function or a "declare the signature explicitly" registration
path that undercuts a load-bearing rule. Worse, it means testing a program compiled against a
*parallel vocabulary*: any drift between the mock language and the real one — a differing signature,
an unreproduced pattern — makes the test pass while production breaks, and nothing detects it. That
is exactly the backend-divergence failure the core tree exists to prevent.

*Swap `Implementation.instance`.* Does not work. `Implementation` is
`(Class owner, Method method, Object instance)` and dispatch is `method.invoke(instance, args)`,
which requires an instance of the declaring class. Implementation classes are `final`.

Interception keeps the artifact under test identical to the artifact that ships, needs no
enumeration of the vocabulary to build a shadow language, and duplicates no signatures. Its cost is
a seam in the runtime, which must stay narrow and honestly named — an interceptor consulted at
`Machine`'s two call sites, not a general plugin point.

### Two interpreters, joined by services

A test run is two BUBAS programs:

| | language | what it is |
|---|---|---|
| the test | BUNIT vocabulary | declares mocks, supplies arguments, asserts |
| the subject | the embedder's real language | the program being tested |

The test's `RUN` statement executes the subject on its own interpreter, with the interceptor
installed. Both sides reach the same recorder through the existing service mechanism —
`ctx.service(...)` — so BUNIT needs no new plumbing to connect them. Services became registrable on
the language as well as the run, which is what makes this cheap.

A sketch of the Java side, close to what was suggested in discussion:

```java
BubasMock mock = BubasMock.of(Interpreter.of(subject));   // installs the interceptor
TestResult result = BunitRunner.of(language)
        .subject(subjectSource)
        .test(testSource)
        .run();
```

`TestResult` is the real product: pass or fail per assertion, the recorded call log, and
diagnostics carrying line numbers. Every front end is a thin adapter over it.

## The test vocabulary

**Settled: the subject's names are STRING literals, never syntax.** Writing
`GIVEN LOAD_ORDER WITH 42` would require the test language to register the subject's vocabulary,
and BUNIT's own keywords would then compete with the embedder's for reserved words in a merged
language. Strings keep the two vocabularies completely disjoint: BUNIT reserves only its own words,
and works unchanged against any embedder's language.

*Verified — this compiles today:*

```basic
PROGRAM ApproveOrderOverLimit
    "LOAD_ORDER"  WITH 42   RETURNS "o1"
    "ORDER_TOTAL" WITH "o1" RETURNS 1500.00

    ARGUMENT "orderId" IS 42
    ARGUMENT "limit"   IS 1000.00

    RUN

    RESULT IS FALSE
    "APPROVE"   WAS NOT CALLED
    "LOG_EVENT" WAS CALLED WITH "INFO", "over limit: 1500.00"
END.
```

Patterns need at least one literal but need not begin with a keyword, so
`{literal/STRING:name} WAS NOT CALLED` is legal and overlap analysis accepts it alongside
`{literal/STRING:name} WITH …`.

There is no `GIVEN`/`WHEN`/`THEN` prefix. Gherkin's phase keywords earn their place when steps are
free text; here every statement is typed and its verb already says which phase it belongs to, so
the prefix costs a word per line and carries no information. Phase ordering — setup before `RUN`,
assertions after — is checked by the runner before execution rather than encoded in syntax.

### Consequences to design around

**Statement patterns have no variadic form.** This is about patterns, not calls: a function call
`PAIR("a", "b")` is parsed by the expression parser and multi-argument calls are fine. But inside a
*pattern* an expression stops at a comma, so one placeholder cannot absorb an argument list. BUNIT
ships one pattern per arity — `WITH {e}`, `WITH {e}, {e}`, and so on to some N — which is a hard
ceiling on how many arguments a mocked call may be written with, independent of whether BUBAS
functions themselves gain varargs.

**No user-defined blocks.** `IF`, `DO` and `FOR` are structural, not pattern-defined, so BUNIT
cannot introduce a `TEST … END TEST` block. One file is one test case, exactly as the `.bu` corpus
in `bubas-test` already works.

## Assertions

Only effects are observable, which is the right constraint regardless — asserting on a program's
internals is how suites become change detectors. A test may assert on:

- the subject's return value
- which commands ran, with which arguments, how many times, in what order
- which functions were called, with which arguments
- what was logged
- that the subject failed, with a given message and line

Variables are not observable and should stay that way.

## Partial mocking and services

An unmocked function runs for real, and a real function may need `ctx.service(...)`. So services
are not irrelevant to BUNIT — they are irrelevant only to *fully* mocked runs.

Since services now live on the language, a test language that copies the embedder's services mostly
works without ceremony. What is not yet decided is what should happen when an unmocked function
demands a service the test never supplied: fail the test with a diagnostic naming the function and
the service, or fail the run as an ordinary missing-service error. The first is far more useful and
is probably worth the specificity.

### Running against the real vocabulary

Because mocking is an interceptor rather than a different language, **not installing it runs the
same test against the real implementations.** Mock declarations would then have to be skipped or
rejected, which needs a decision, but the property is worth preserving: it turns the mock-fidelity
gap from an unfixable limit into a second mode over the same test file.

## Required API extensions

Prerequisites, not details:

1. **`BubasLanguage` cannot enumerate its vocabulary.** It looks up a function or opaque type by
   name and lists `commands()`. BUNIT needs `functions()` and `opaqueTypes()` to validate mock
   targets and to type tokens.
2. **A dispatch seam in the runtime.** Narrow, named for what it is, consulted at `Machine`'s
   function-call and command-invocation sites.
3. **Signature access for typing tokens.** A mock returning a token for `LOAD_ORDER` gets the
   token's type from the function's declared return type; `FunctionSignature` already carries it.

## Open questions

1. **Telling a token from a string.** `WITH "o1"` is ambiguous: the STRING `"o1"`, or the token
   named `o1`? Resolving it from the mocked function's signature — a string literal in an
   opaque-typed position is a token — is concise but implicit, and this codebase dislikes implicit.
   Distinct syntax (`WITH TOKEN "o1"`) is explicit but multiplies patterns combinatorially once
   arguments mix tokens and values. **This is the sharpest unresolved question in the design.**
2. **Arity ceiling.** How many `WITH` arities to ship, and what the diagnostic says when a function
   exceeds it.
3. **Ordering assertions.** Whether call order is assertable, and with what syntax.
4. **Unmocked calls.** Does calling an unmocked function fail the test, run for real, or return a
   default? Failing is the strict answer and matches the language's temperament.
5. **Mock verification.** Whether a declared mock that is never called fails the test, as an unused
   variable does.

## Modules

`bubas-bunit` depends on api, analyser and runtime. It is a framework, not a function library, so
the rule that keeps `bubas-support` on the API alone does not apply.

`bubas-mcp` should **not** be built yet. MCP, REST and CLI are all thin adapters over `TestResult`;
committing to an MCP SDK before that record has settled would shape the API around one transport.
Get the runner and its result type right, and each adapter is a short file.
