# CLAUDE.md — working in this repository

## What this repository is

BUBAS is an orchestration language for subject matter experts, embedded in Java applications.
See [`README.md`](README.md) for the pitch and [`SPEC.md`](SPEC.md) for the language definition
and API contract.

## The specification is the contract

`SPEC.md` is normative. When code and specification disagree, the specification wins unless the
change was discussed and `SPEC.md` updated in the same commit. Do not "fix" a behaviour to match
the code.

Every rule in `SPEC.md` was decided deliberately, and several look arbitrary until you know the
reason. Before changing one, check whether it is load-bearing. These are the ones most often
mistaken for oversights:

- **Every literal token of every pattern is a reserved word.** This is not namespace greed. It is
  what makes expression boundaries decidable without backtracking: an expression ends at the
  first reserved token.
- **A condition the compiler can answer is an error, and "can answer" is flow-sensitive.** `n = 5`
  followed by `IF n > 10` is rejected: the variable is not constant, but it is settled where the
  condition reads it, and almost nobody writes `IF FALSE` — the literal case alone would be a
  formality. What the analysis learns about a variable comes only from a command declaring
  `@BubasAssigns` — repeatable, since one statement may fill several variables and may declare only
  some of them — and anything else handed to a command is assumed written, because `set` is not
  guarded. A value believed and not held rejects a correct program; a value forgotten only lets a
  mistake through, so the analysis is deliberately timid. A program parameter is the one value it
  can never know, and is the answer for anything a test has to vary.
- **Constants are folded, and the folding is not the point — the rejections are.** Evaluating what
  is fixed at compile time makes dead branches, dead loop bodies and always-trapping arithmetic
  visible, and [`SPEC.md` §8.3](SPEC.md#83-rejected-at-compile-time) rejects every one of them.
  Nothing is deleted: a compiler that quietly dropped the dead branch would hide the mistake it
  just found, so `IF FALSE` is an error rather than a no-op. Decimal *division* folds too, because
  the `MathContext` is sealed into the `BubasLanguage` — which is also why a folded constant
  belongs to the program and the language together, not to the source. See
  [`CONSTANTS.md`](CONSTANTS.md).
- **A run's limits are per run, and the array limit is one a command has to ask for.**
  `maxSteps` counts statements *and* loop passes, because a pass is work in its own right — a
  `WHILE` evaluates its condition, a `FOR` moves and tests its counter — and pricing that at
  whatever the body happens to contain would be arbitrary. `maxArrayLength` cannot be enforced by
  the runtime on a command's behalf: an array that has reached a variable is memory already spent,
  so the only useful moment to refuse is inside the command, before `Array.newInstance`. That makes
  it an obligation on any vocabulary that allocates, the same shape as a handler having to be
  thread-safe. Both default to unlimited, and neither changes what a program means. Both are read
  from `CoreContext`, not `Context`: a budget belongs to whoever is doing the work, and the compiler
  will one day be doing some of it — an analysis that follows a loop it can see through is executing
  it, and needs bounding for the reason a run does.
- **Purity is declared, never inferred: `@BubasMemoizable` is what lets the compiler call a function.**
  Nothing about a signature says whether it reads a clock or a database, so an unannotated function
  is opaque however pure it looks. One part of the claim is a type and not a promise: a static
  function's method takes `CoreContext`, which has no `service` on it, so reaching an application
  does not compile, and `seal()` refuses such a function declaring `Context` instead. Nothing can
  check the rest, so a false declaration silently bakes in an answer a run would not have given. Folding is further limited to
  scalar parameters and results, because arrays, opaque values and wildcards are marshalled by the
  interpreter and the compiler has no marshaller. `ctx.error` while folding is a compile error, and
  meant to be.
- **`CoreArithmetic` has one implementation of every operation and both callers reach it.** The
  interpreter executes them and `ConstantFolding` evaluates them at compile time. Two copies would
  agree on everything but the edge cases and disagree invisibly, which is the characteristic bug of
  having a folder at all. Do not inline an operation into either caller.
- **A function cannot touch the variable store at all** — not even to read. Arguments in, value
  out. Only a statement handler reaches variables, and only as its pattern's pre- and
  postconditions declare. A by-name read would be a use the definite-assignment analysis never
  sees, of a type nothing checked, possibly before assignment. Ambient configuration that many
  functions share is a service, not a global.
- **There is no NULL in the language.** `null` is a Java value that opaque slots may hold. Adding
  a null literal or a null test reopens a design that was deliberately closed.
- **`+` coerces only when the left operand is a STRING.** The asymmetry is intentional; `42 + "x"`
  is an error and `"" + 42 + "x"` is not. The operator `+` is assimetrical in nature when applied 
 to strings, `"a" + "b"` is not the same as `"b" + "a"`.
- **Patterns match whole logical lines.** Not prefixes, not longest-match. Two patterns matching
  one line is an error, not a resolution problem.
- **One class is one function or one command, and nothing names a method in a string.** A lambda
  is anonymous, so generated code could only reach it by string key through a registry that would
  have to be rebuilt first; a method name in a string literal rots silently under IDE rename. The
  class reference is the only reference that refactoring tools maintain. This is also what allows
  signatures to be derived instead of declared twice.
- **Implementation classes are constructed by the runtime with no arguments.** They cannot capture
  embedder state, which makes `ctx.service(...)` the only way to reach a dependency. Adding a
  constructor-injection convenience undoes that.
- **`VariableArg` omits `declare()` and `isInitialized()` on purpose.** The runtime creates the
  slot, because a variable-creating placeholder must carry a type constraint and so name, type and
  finality are all fixed statically — declaring is the framework's job, and only the value needs
  the handler. Initialization is the analyser's job: a handler may read exactly where the pattern
  declared an `initialized` precondition, and where it writes, prior state is irrelevant.
  `type()` and `isFinal()` look like the same kind of member but are not: a pattern can leave both
  open (`/NUMBER`, or no mutability prefix), and the two cases cannot be split into separate
  patterns because their token shapes are identical and overlap analysis rejects the pair. Adding
  the missing two back re-implements checks the analyser already made.
- **Declarations are top level only, and the rule is about patterns, not about `DECLARE`.** Any
  statement whose pattern creates a variable — a `new` precondition or a `final` postcondition —
  may appear only in the program body, never inside an `IF` arm, a `DO` or a `FOR`. BUBAS has no
  local variables, so a declaration inside a block would look scoped while being global: it would
  outlive the block and collide with a later declaration, none of which the indentation suggests.
  The simpler analysis that follows — declaredness needs no flow analysis at all — is a
  consequence, not the reason, so do not relax the rule to buy convenience back.
- **Arrays are invariant, unlike Java's.** `Order[]` accepts only `Order[]`, never `RushOrder[]`.
  An array crosses into Java as the interpreter's backing store, so covariance would let a handler
  declaring `Order[]` store a plain `Order` into an array the script declared as `RushOrder` —
  caught, if at all, by an `ArrayStoreException` inside embedder code with nothing naming the line
  that passed it. This costs read-only functions the ability to take an array of a subtype; that is
  the accepted price, not an oversight. Array assignability is consulted in exactly one place,
  matching an argument to a parameter, because arrays are never assigned and never returned.
- **A `var` reaches exactly one location, and there is no `get(index)` / `set(index, value)`.**
  Given `MODIFY A[5]` the handler alters `A[5]` and has no way to reach `A[6]`. That is the
  guarantee the script author reads off the line. An array-typed placeholder is the opposite and
  deliberately unrestricted: `RESET A FROM 3 TO 7` hands over the backing store, and the handler
  may write whatever it likes. The difference is visible in the source — `A[5]` names a slot, `A`
  names the array.
- **A variable may not be named after its type.** Registered opaque type names are reserved like
  everything else, so `DECLARE order Order` is rejected. It is the same rule that bans `userId`
  beside `UserID`, but it bites where people least expect it, so the diagnostic must name the type
  rather than just report a reserved word.
- **Assignment has no keyword, so a pattern need not begin with one.** `x = 5` is the built-in
  assignment pattern; its only literal is `=`. Requiring a leading word would make the most
  frequent statement in the language inexpressible. What a pattern must have is at least one
  literal of any kind — one made only of placeholders reserves nothing and would match by shape.
- **Trivia has exactly one owner, and lexing is lossless.** Everything between two tokens belongs
  to the earlier token; everything before a line's first token belongs to the line; a terminator
  belongs to the line it ends. Leading-plus-trailing trivia gives every gap two plausible owners
  and a tie-break rule nobody remembers. A blank or comment-only line is a zero-token logical line
  owning its own trivia, which is why there is no file-level trivia slot and why the parser must
  skip lines with no tokens.
- **`Registrar` lives in `bubas-api`, not beside the builder it narrows.** It is the interface a
  bundle of definitions is handed — the `define` calls and `install`, never `seal()` or
  `skipOverlapAnalysis()`, which are the embedder's decisions rather than a library's. Putting it
  in the analyser would mean every third-party function library had to depend on the interpreter
  front end to publish a bundle, which is the exact coupling splitting the API out was meant to
  prevent. `bubas-support` is the proof: it registers the whole standard module through
  `Standard::register` while still requiring `bubas.api` alone. The narrowing is static and a
  caller can cast back to `Builder`; it keeps an honest bundle in its remit and is not a sandbox.
- **A wildcard is a parameter and never a return type.** `Value` maps to `ANY` and `BubasArray` to
  `ANY_ARRAY`, both accepting almost anything in a signature; neither may be returned, and `seal()`
  says so. A returned wildcard would put a value of unknown type into the script, where nothing
  downstream could check it. Parameters are safe because the unknown never propagates: it reaches
  Java and stops.
- **Only the spread form calls a variadic function.** Java accepts `join(array)` and
  `join("a","b")` alike; BUBAS takes only the second. Arrays here are invariant first-class values,
  and admitting both revives the overload ambiguity the single form avoids. An embedder wanting an
  array declares an array parameter. A *command* may not be variadic at all — its parameters match
  pattern placeholders, which are fixed in number.
- **A command's name is its pattern skeleton, and `@BubasCommandName` replaces it rather than
  aliasing it.** Naming by keyword was rejected because keywords are not unique — `DECLARE` names
  four patterns — and a keyword-only name is *lossy*: `PAY {var}` and `PAY {var} = {e}` both reduce
  to `PAY`, and no rule can say which was meant. JNI is the cautionary precedent: a short decorated
  name binds fine until an overload appears, then resolves to nothing. An identifier derived by
  dropping information is stable only until the set it is drawn from grows.
- **The BUNIT framework must not depend on its own DSL.** `bubas-bunit` knows no statement by name;
  a statement declares what it does through annotations the framework defines, and the framework
  reads those. Adding a `switch` on a keyword there would make the vocabulary unswappable, which is
  the only thing the module split buys. It is the same rule as the language's own: `DECLARE` is an
  ordinary pattern, not a built-in.
- **A name is defined once; replacing it must be said with `override()`.** Redefinition used to be
  silently accepted, the later definition winning — so two bundles both defining `LENGTH` produced a
  language whose behaviour depended on installation order, with no diagnostic. That is the hazard
  opt-in registration exists to prevent, arriving by a different door. `override()` covers exactly
  one definition and is then spent; it fails when the name is absent, because an override of nothing
  is an unfinished rename. `overrideAll()` is its counterpart for a map, and a pending flag may not
  cross `install()` or reach `seal()`.
- **A description must never restate anything derivable, and only the export demands one.**
  Signatures, types, arity, patterns, pre- and postconditions all appear beside the prose in an
  export; prose repeating them adds nothing and eventually contradicts them, and the prose is the
  half that is wrong. Requiring descriptions at `seal()` was considered and rejected: a missing one
  is not a bug — the language runs perfectly — and forcing prose produces `@BubasDescription("Loads
  an order")` on `LOAD_ORDER`, which is noise shaped like documentation. The gate belongs on
  `VocabularyExport`, which is the only thing that suffers from a hole. `@BubasReviewed` moved
  there for the same reason after being built at `seal()` first: a checksum fires on any change to a
  described class, most of them ordinary development, so refusing to seal would break startup and
  every test of an application generating no documentation — whose rational answer is to delete the
  annotation. A check that fires too widely destroys what it protects. `bubas-export` is a module
  nothing depends on, so an inventory generator never reaches production by accident.
- **Extension registration is opt-in, discovery is not** — and discovery is planned, not built.
  `ServiceLoader` finds whatever is on the classpath; the builder decides what gets registered.
  Registering automatically would let an unrelated jar reserve a word an existing script uses as a
  variable, breaking it with no change to the script or the embedding code. Nothing in SPEC §10.5
  exists yet, and the rationale there argues it may never need to: a bundle installed with
  `install(Acme::register)` gives a library the same packaging while keeping the vocabulary a name
  the embedder wrote down. Do not implement discovery as a tidy-up; it is a design decision that is
  still open.

## Build and layout

Every artefact ships a `module-info.java` and a `META-INF/services` entry, so extension discovery
works for embedders on the module path and on the classpath alike.

| Module | Contents | Depends on |
|--------|----------|-----------|
| `bubas-api` | `BubasType`, `TypeNames`, `Value`, `Context` interfaces, `VariableArg`, `ExpressionArg`, `LiteralArg`, `BubasArray`, `BubasException`, `Registrar`, `BubasCallInterceptor`, `@BubasCommandName`, the extension SPI | — |
| `bubas-lexer` | Tokens, logical-line assembly, continuation and comment handling | api |
| `bubas-analyser` | `BubasLanguage`, `BubasProgram`, pattern matcher and overlap analysis, parser, lowering, definite assignment | api, lexer (transitive) |
| `bubas-runtime` | `Interpreter`, dispatcher, variable store | api, analyser |
| `bubas-support` | Mandatory prelude and the optional packages | api |
| `bubas-test` | The `.bu` script corpus and the runner that executes it | api, analyser, runtime, support (test scope) |
| `bubas-export` | `VocabularyExport`: a sealed language described for a generator or a person | api, analyser |
| `bubas-bunit` | The mocking framework: recorder, `BubasCallInterceptor`, consistency checker, `TestResult` | api, analyser, runtime |
| `bubas-bunit-matchers` | `ARGS`, `Arguments` and the matchers, usable by any BUNIT vocabulary | api, bunit |
| `bubas-bunit-commands` | One DSL over it: the statements a BUBAS unit test is written with | api, bunit, matchers |
| `bubas-bunit-standard` | The assembly an application depends on: sealed test language and `BunitSuite` | the three above, analyser, support |

The pattern matcher sits with the parser deliberately. They would be separable only at the cost of
an interface module and runtime injection of its implementation, to solve a dependency that does
not exist: every pattern, function and opaque type is registered and the language sealed before the
first source line is matched, so the parser's vocabulary is complete and immutable by the time it
runs — a plain field, not an injected service.

`BubasLanguage` owns `compile()`, so it belongs with the analyser, not the runtime. Nothing is
resolved by name at run time: the compiler bakes resolved implementations and classes into the AST,
so the registries never outlive analysis. Execution is entered with `Interpreter.of(program)` —
a factory method on `BubasProgram` would make the analyser depend on the runtime.

`bubas-codegen` joins in phase 3. `bubas-support` depends only on `bubas-api`, which is the point
of splitting the API out: a third party writing a function library must not have to depend on the
interpreter.

`bubas-analyser` declares `requires transitive bubas.lexer` because its own public API returns
`LogicalLine` — a core tree or a diagnostic cannot be read without it.

Tests run on the classpath rather than the module path because they exercise package-internal
behaviour. The `-parameters` compiler flag is on because BUBAS parameter names are derived from
Java parameter names.

`bubas-test` is that module for phase 1, and it is where the conformance suite belongs when
code generation arrives: the same corpus, run through a second backend.

## Conventions

- Java types are prefixed `Bubas` — `BubasLanguage`, `BubasProgram`, `BubasType`,
  `BubasException`.
- BUBAS is the language. The implementation is "the BUBAS interpreter". A source file holds a
  BUBAS program.
- In prose, "line" always means a logical line — after continuation joining and comment
  stripping.

## Testing

Two obligations beyond ordinary unit tests:

1. **Diagnostics are part of the contract.** A test that only asserts "compilation failed" is
   insufficient; assert the message and the line. The strictness in this language is only
   valuable if it explains itself.
2. **Conformance between backends.** Once code generation exists (phase 3), every test program
   must run through both the interpreter and the generated Java and produce identical results
   *and* identical errors. Divergence between the two is the characteristic bug of that design,
   and nothing else will catch it.

### The script corpus

`bubas-test/src/test/resources/scripts/**/*.bu` holds whole programs, each run end to end by
`ScriptTest`. A script declares its own expectation in a header comment, so adding a case means
adding one file and nothing else:

```
'NO-COMPILE                  ' or 'RUN-TIME-ERROR, or 'OK
' What this script is for, in a sentence.
' ERROR: a fragment the diagnostic must contain
' LINE: 8
```

`ERROR` and `LINE` are optional but expected for the two failing outcomes — asserting only that
compilation failed is exactly the insufficiency rule 1 is about. An `'OK` script proves itself with
`ASSERT "what is being claimed", <condition>`, a command the test environment registers, so a
script that runs to the end has checked its own results rather than merely not crashing.

Two things to know before adding scripts:

- **Line numbers include the header**, so the first statement of a five-line header sits on line 6.
  Rather than counting, guess and run: the runner prints the full diagnostic it got, including the
  line it names, which is the number to write down.
- **A variable nothing reads is a compile error**, so a script probing a later stage will trip the
  unused-variable check first and report that instead. Give every variable a reader — usually the
  trailing `ASSERT "unreachable", ...` the failing scripts carry.

