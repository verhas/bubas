# CLAUDE.md — working in this repository

## What this repository is

BUBAS is an orchestration language for subject matter experts, embedded in Java applications.
See [`README.md`](README.md) for the pitch and [`SPEC.md`](SPEC.md) for the language definition
and API contract.

**The repository currently contains no code.** The specification is settled; implementation has
not begun.

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
- **A function may read variables but never write them.** Only a statement handler may modify the
  store, and only as its pattern's postconditions promise. Loosening this destroys definite
  assignment.
- **There is no NULL in the language.** `null` is a Java value that opaque slots may hold. Adding
  a null literal or a null test reopens a design that was deliberately closed.
- **`+` coerces only when the left operand is a STRING.** The asymmetry is intentional; `42 + "x"`
  is an error and `"" + 42 + "x"` is not. The operator `+` is assimetrical in nature when applied 
 to strings, `"a" + "b"` is not the same as `"b" + "b"`.
- **Patterns match whole logical lines.** Not prefixes, not longest-match. Two patterns matching
  one line is an error, not a resolution problem.

## Assumptions to confirm before writing code

These are not yet decided. Ask rather than guess:

- build tool (Maven assumed) and Java version (21+ assumed, for records and sealed types)
- module layout: single artefact, or core / stdlib / codegen split
- test framework and whether the conformance suite is a separate module

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

