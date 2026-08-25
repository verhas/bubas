# BUNIT — unit testing BUBAS programs

**Design notes.** BUNIT is built and runs; this file records *why* it is shaped as it is, which
the code cannot say for itself. Everything shown here works unless a section says otherwise, and
the questions still open are gathered at the end.

```basic
PROGRAM OverLimitIsRejected
    "LOAD_ORDER"  WITH ARGS(42)   RETURNS "o1"
    "ORDER_TOTAL" WITH ARGS("o1") RETURNS 1500.00
    "APPROVE _" IS MOCKED

    ARGUMENT "orderId" IS 42
    ARGUMENT "limit"   IS 1000.00

    RUN

    RESULT IS FALSE
    "APPROVE _" WAS NOT CALLED
END.
```

```java
var results = BunitSuite.of(myLanguage, subjectSource).runAll(tests);
if (!BunitSuite.allPassed(results)) {
    System.out.println(BunitSuite.report(results));
}
```

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
enumeration of the vocabulary to build a shadow language, and duplicates no signatures.

**Built.** `BubasCallInterceptor` in `bubas-api`, installed per run with
`Interpreter.of(program).intercept(recorder)`. It carries the `Bubas` prefix because it is part of
the interpreter's API even though nothing outside a test framework should reach for it. Four
methods, two predicates and two calls, so there is no tri-state to encode: a `VOID` function and an
unmocked one are different answers, not one nullable one.

```java
boolean interceptsFunction(String name);
Value   onFunction(String name, List<Value> arguments);
boolean interceptsCommand(String pattern);
void    onCommand(String pattern, StatementContext context, Map<String, Object> arguments);
```

A function's arguments arrive evaluated, boxed with their static types, and **spread rather than
packed** for a variadic call — a mock matches on what the script wrote, not on how the Java method
receives it. A command receives **the handler's own argument objects**, keyed by placeholder name,
which is what lets an interceptor write what the real command would have written.

Installed on the interpreter and never on the language, so a sealed language knows nothing about
testing; not installing one runs the real implementations, which is what makes an integration mode
free.

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
    "LOAD_ORDER"  WITH ARGS(42)   RETURNS "o1"
    "ORDER_TOTAL" WITH ARGS("o1") RETURNS 1500.00

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

**Because names are strings, the BUNIT language is embedder-independent.** It needs none of the
subject's vocabulary — no opaque types, no functions, no patterns — so it is a constant: sealed
once, shared across every embedder and every test. Only the *runner* needs the subject's language,
to validate mock targets and to run the subject. Avoiding reserved-word collisions was the first
reason for strings; this is the second, and it removes the whole two-language merge problem.

### Naming what you mock

A function is named as registered — `"LOAD_ORDER"`. A command is named by its **skeleton**, the
pattern with every placeholder written `_` ([SPEC §10.1](SPEC.md#101-one-class-one-thing)):

```
"VALIDATE _ AGAINST _" WAS CALLED
"LOG_EVENT _, _" WAS CALLED WITH "INFO", "over limit: 1500.00"
```

Naming by keyword alone was considered and rejected: keywords are not unique — `DECLARE` names four
patterns — and a keyword-only name is *lossy*, so `PAY {var}` and `PAY {var} = {e}` both reduce to
`"PAY"` and no rule can say which was meant without inventing a marker to mean "and nothing more".
The skeleton is total, so the question never arises.

Dropping `_` when a shorter form would be unambiguous was considered and rejected for a sharper
reason: uniqueness depends on the whole language, so extending the vocabulary would silently
invalidate tests written earlier. A name must not stop meaning what it meant.

JNI has the same defect and is the cautionary precedent. A native method binds to the short
decorated name `Java_com_example_Foo_bar` only while it is not overloaded; add `bar(String)` beside
`bar(int)` and both must use the long form carrying the argument signature, so the C function that
worked yesterday resolves to nothing and the failure arrives as an `UnsatisfiedLinkError` at the
first call. Nobody touched the working binding — somebody else's addition invalidated it. A short
name that is unique *for now* is a lossy encoding, and lossiness only bites once the set it is drawn
from grows.

`@BubasCommandName("LoanValidation")` replaces the skeleton for teams that prefer a domain name.
Once a command is named, **its skeleton no longer refers to it** — a mock naming the skeleton of a
named command is an error, not a fallback.

There is no `GIVEN`/`WHEN`/`THEN` prefix. Gherkin's phase keywords earn their place when steps are
free text; here every statement is typed and its verb already says which phase it belongs to, so
the prefix costs a word per line and carries no information. Phase ordering — setup before `RUN`,
assertions after — is checked by the runner before execution rather than encoded in syntax.

### Consequences to design around

**A mocked command must supply what its pattern writes.** A pattern with a `new` precondition or
an `initialized` postcondition on a `var` placeholder assigns a variable. If a mock merely records
the call, the script reads an unassigned slot at run time — and definite-assignment analysis passed
at compile time, so nothing warned.

The rule: **an opaque target is written automatically by the framework** — a token, which is the
only thing that could go there, since BUBAS cannot construct an opaque value and so neither can the
test. **Everything else the command writes must be set by the mock**, and a mock that fails to set
it is an error at mock-assembly time, not a surprise at run time.

**Statement patterns have no variadic form** — which is why an argument list is a *value*. This is
about patterns, not calls: a function call `PAIR("a", "b")` is parsed by the expression parser and
multi-argument calls are fine. But inside a *pattern* an expression stops at a comma, so one
placeholder takes one argument and a pattern per arity is the only alternative — a ceiling rather
than a design, and one that made a three-argument function unmockable.

`ARGS(42, "EU")` collects them into an `Arguments` value instead, and the pattern reads
`{literal/STRING:name} WITH {expression/Arguments:args} RETURNS {expression:value}`. One pattern,
any count. Because `Arguments` is an opaque type the **type checker** enforces the form: nothing but
a call to `ARGS` can produce one.

The two forms cannot coexist. `{expression:a}` and `{expression/Arguments:a}` are the same shape in
token classes, so overlap analysis rejects the pair — the collecting form replaces the fixed-arity
ones rather than joining them.

**No user-defined blocks.** `IF`, `DO` and `FOR` are structural, not pattern-defined, so BUNIT
cannot introduce a `TEST … END TEST` block. One file is one test case, exactly as the `.bu` corpus
in `bubas-test` already works.

## Argument lists and matchers

`ARGS` collects any number of arguments into a value, and any of them may be a **matcher** — an
object that judges the argument instead of being compared to it.

```basic
"RATE" WITH ARGS("EU", BETWEEN(1, 5)) RETURNS 0.20
"LOG _, _" WAS CALLED WITH ARGS("INFO", CONTAINS("rate for EU"))
```

`ARGS(parts ANY...) -> Arguments` is variadic and takes `ANY`, so values and matchers mix without
anything being declared twice. `Matcher` has two methods: `matches(Value)` and `describe()` — the
second so a failure reads *expected … containing "over limit"* rather than leaving the reader to
guess what was wanted.

**They are their own module.** `bubas-bunit-matchers` depends on the framework and not on the
statements, so a vocabulary that replaces every statement still gets `ARGS` and the matchers
unchanged. A matcher is an ordinary object judging an ordinary value, which is also why its tests
need no language at all.

Two ship: `BETWEEN(low, high)`, inclusive at both ends, taking either numeric type because its
bounds are DECIMAL; and `CONTAINS(text)`, the commonest string expectation by a wide margin.

Named but deliberately absent, each a few lines and none yet asked for: `ANYTHING()`,
`GREATER_THAN`, `AT_LEAST`, `LESS_THAN`, `AT_MOST`, `NOT`, `STARTS_WITH`, `ENDS_WITH`, `MATCHES`.
Present tense, not `-ING`: a matcher states what always holds of the argument, not what is happening
to it, and the shorter form reads better. An embedder needing one writes it against `Matcher` and
registers it alongside the standard set — nothing in the framework has to change, and the tests of
the matchers module show exactly that being done.

`MATCHES(regex)` is the one I would think hardest about: it embeds a second language inside a
language whose whole claim is that a domain expert can read it.

**A matcher judges whatever it is handed.** A `BETWEEN` given a STRING does not match; it does not
fail. An expectation that a mismatched *type* should be an error is a different assertion, and
conflating the two would make every matcher a type check as well.

## The mock consistency checker

A test is a BUBAS program, so the language compiles it: syntax, types, definite assignment. That is
compile time, and it is not enough. Mock declarations are *statements*, so what a mock sets is a
flow-sensitive property — a mock declared inside one arm of an `IF` and not the other would leave a
test that passes on some runs and fails on others, for reasons the author never sees.

So after compilation, and before execution, the test program's core tree gets a second pass: a mock
consistency check. It walks the same shape the flow analyser walks, tracking for each mocked call
what the mock supplies, and merging at joins the way definite assignment merges — the guarantee is
what holds on *every* path, not what some path happened to set.

What it catches, all before the subject runs:

- a mocked command that does not supply a non-opaque variable its pattern writes
- a supply on one path and not on another
- a name the subject's language does not have, as a function or a command
- a supply for a variable the command does not write, or for a command that is not mocked
- a mock declared for the wrong number of arguments, counted through `ARGS` as well
- a mock answering a type the function does not return
- an argument for a parameter the subject does not take
- an expectation before the act, or a test with no act at all

**How it knows any of this without knowing the vocabulary.** A statement declares what it does with
annotations the framework defines — `@NamesTarget`, `@DeclaresMock`, `@SuppliesVariable`,
`@NamesParameter`, `@MatchesArguments`, `@SuppliesResult`, `@Act`, `@Expectation` — each naming a
*placeholder* rather than carrying a value. The checker reads the constant sitting at that
placeholder in the core tree. So it learns that `"LOAD_ORDER" RETURNS 1` is about `LOAD_ORDER`
because the class said the name is in the placeholder called `name`, never because anything in the
framework knows the word `RETURNS`.

Counting arguments is the one place the checker reads the *shape* of an expression rather than a
constant: `@CountsArguments` says which placeholder holds a call whose own arguments are the count.
That is acceptable here and nowhere else — it is static analysis, done once, by the component whose
job is to walk the tree, and it reads the shape rather than the name, so a vocabulary whose
collector is not called `ARGS` works unchanged. A *handler* doing the same at run time is the thing
to avoid, and cannot be done anyway: `ExpressionArg` offers only evaluation.

Those placeholders must be `{literal/STRING:…}`. The check runs before the test does, so a computed
name would not be there to read, and the checker says so rather than skipping the statement.

The merge rule needed one case that is not obvious: **a command mocked in only one branch keeps
that branch's supplies** rather than intersecting to nothing. Mocking conditionally with a matching
conditional supply is correct — on the other path the real handler runs and does its own writing —
so intersecting blindly would reject a legitimate test.

This is the same argument that kept `NULL` out of the language. A value that might not be there
turns every use into a question, and the answer is to prove it is there rather than to check at
each use. A mock that might not be configured is the same defect one level up.

## Assertions

**Settled: fail fast.** A test file is one test case, so the first failed expectation ends it, which
is what `ctx.error(...)` does naturally. `TestResult` still carries a list, so collecting every
failure later would not change its shape.

**The subject's log is captured but not assertable.** A mocked handler never logs and an unmocked
one is Java, which is outside BUNIT's remit; a script that logs deliberately is asserted through the
call record instead. The runner installs a capturing logger only because the default one prints to
standard output, and keeps the text in `TestResult` as a transcript for a failed test.

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

1. ~~`BubasLanguage` cannot enumerate its vocabulary.~~ **Done.** `functions()` and
   `opaqueTypes()` list it in registration order.
2. ~~A dispatch seam in the runtime.~~ **Done.** `BubasCallInterceptor`, above.
3. ~~Signature access for typing tokens.~~ **Done.** `FunctionSignature` carries the declared
   return type, and `Token.named(expected, given)` is the single rule both the checker and the
   recorder use, so the two cannot disagree about what counts as a token name.
4. ~~A name for each command.~~ **Done.** `StatementPattern.skeleton()`, `CommandDefinition.name()`
   and `@BubasCommandName`.
5. ~~`requires bubas.lexer` was not transitive in the analyser.~~ **Done.** Its public API returns
   `LogicalLine`, so nothing could walk a core tree without redeclaring the dependency.

## Open questions

1. **Telling a token from a string — resolved.** A token stands in only for an *opaque* value,
   because those are the only values BUBAS cannot construct. So a STRING literal in an opaque-typed
   parameter position names a token, and anywhere else it is a string. The runner knows the
   signature before dispatch, so this is decidable rather than magic, and reportable when it
   surprises someone. A token in an `ANY` position stays ambiguous and is rejected, with a
   diagnostic saying to mock a concrete signature instead.
2. ~~Arity ceiling.~~ **Gone.** `ARGS` collects an argument list into a value; see above.
3. ~~Argument matchers.~~ **Built**, two of them. See below.
4. **Ordering assertions.** Whether call order is assertable, and with what syntax.
5. **Unmocked calls.** Does calling an unmocked function fail the test, run for real, or return a
   default? Failing is the strict answer and matches the language's temperament. Today it runs for
   real, and a token reaching a real handler fails as a Java type mismatch.
6. **Mock verification.** Whether a declared mock that is never called fails the test, as an unused
   variable does.
7. **A `.bu` corpus for BUNIT.** The language has 67 whole-program scripts in `bubas-test`; BUNIT
   has none, and its tests are Java text blocks.

## Modules

Three, and the direction between them is the design rather than an accident of packaging.

| module | is | depends on |
|---|---|---|
| `bubas-bunit` | the mocking framework — recorder, interceptor, consistency checker, `TestResult` | api, analyser, runtime |
| `bubas-bunit-matchers` | argument lists and matchers, for any vocabulary | api, bunit |
| `bubas-bunit-commands` | one DSL over it | api, bunit, matchers |
| `bubas-bunit-standard` | the assembly an application depends on | both, plus analyser and support |

**The framework must not depend on the DSL.** It would be a framework in name only: swapping the
vocabulary would mean changing the thing the vocabulary is supposed to be independent of. So
`MockRecorder` lives in the framework and the statements call it, never the reverse, and nothing in
`bubas-bunit` names a statement — a class says what it does through annotations, and the framework
reads those.

The cost is that neither module can assemble a language: the framework must not see the DSL, and
the DSL must not see the analyser. `bubas-bunit-standard` exists to do exactly that, and an embedder
wanting a different vocabulary skips it and writes the three lines itself.

One consequence worth knowing: `bubas-bunit`'s own tests cannot use `bubas-bunit-commands`, because
Maven forbids reactor cycles at any scope. That is a feature — the framework has to be tested
against a throwaway vocabulary, which is the only real proof that it is vocabulary-agnostic.



`bubas-mcp` should **not** be built yet. MCP, REST and CLI are all thin adapters over `TestResult`;
committing to an MCP SDK before that record has settled would shape the API around one transport.
Get the runner and its result type right, and each adapter is a short file. `TestResult` and
`BunitSuite.report` are that shape now, which is the argument for having waited.
