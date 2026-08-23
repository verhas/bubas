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
- **There is no constant folding, anywhere.** `DECIMAL` division depends on a `MathContext` that
  can change at runtime, so folding it is unsound. Folding was dropped everywhere rather than
  maintaining a per-operator carve-out.
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
- **Extension registration is opt-in, discovery is not.** `ServiceLoader` finds whatever is on
  the classpath; the builder decides what gets registered. Registering automatically would let an
  unrelated jar reserve a word an existing script uses as a variable, breaking it with no change
  to the script or the embedding code.

## Build and layout

Every artefact ships a `module-info.java` and a `META-INF/services` entry, so extension discovery
works for embedders on the module path and on the classpath alike.

| Module | Contents | Depends on |
|--------|----------|-----------|
| `bubas-api` | `BubasType`, `Value`, `Context` interfaces, `VariableArg`, `ExpressionArg`, `LiteralArg`, `BubasArray`, `BubasException`, the extension SPI | — |
| `bubas-lexer` | Tokens, logical-line assembly, continuation and comment handling | api |
| `bubas-analyser` | `BubasLanguage`, `BubasProgram`, pattern matcher and overlap analysis, parser, type checker, definite assignment | api, lexer |
| `bubas-runtime` | `Interpreter`, dispatcher, variable store | api, analyser |
| `bubas-support` | Mandatory prelude and the optional packages | api |

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

Tests run on the classpath rather than the module path because they exercise package-internal
behaviour. The `-parameters` compiler flag is on because BUBAS parameter names are derived from
Java parameter names.

Still undecided: whether the interpreter/codegen conformance suite is its own module.

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

