# BUBAS Language Specification

**Version** 2.0 (draft) · **Date** 2026-08-20 · **Status** Design settled, implementation not started

BUBAS is an orchestration language for subject matter experts. This document defines the
language, its static semantics, and the Java embedding API. It is the contract between the
BUBAS implementation and the applications that embed it.

---

## Table of Contents

1. [Overview](#1-overview)
2. [Core Principles](#2-core-principles)
3. [Architecture](#3-architecture)
4. [Lexical Structure](#4-lexical-structure)
5. [Type System](#5-type-system)
6. [Expressions](#6-expressions)
7. [Statements](#7-statements)
8. [Variable State and Static Analysis](#8-variable-state-and-static-analysis)
9. [The Pattern System](#9-the-pattern-system)
10. [Java Integration](#10-java-integration)
11. [Errors](#11-errors)
12. [Standard Prelude](#12-standard-prelude)
13. [Code Generation](#13-code-generation)
14. [Worked Example](#14-worked-example)
15. [Reserved Words](#15-reserved-words)
16. [Open Questions](#16-open-questions)
17. [Glossary](#17-glossary)

---

## 1. Overview

BUBAS is a typed, minimal orchestration language. It is **not** a general-purpose programming
language. It exists so that a subject matter expert can sequence business steps, make decisions,
loop over data and call into Java — and nothing else.

### Two layers

| Layer | Written by | Responsible for |
|-------|-----------|-----------------|
| **BUBAS** | Subject matter experts, or an LLM on their behalf | Sequencing, decisions, iteration, calling Java |
| **Java** | Developers | Algorithms, domain objects, infrastructure, persistence, I/O |

### What BUBAS is

- A coordination language for non-programmers
- Statically typed, with all type errors reported before execution
- Extensible: a Java developer defines the vocabulary the script author sees
- Predictable enough for an LLM to generate reliably

### What BUBAS is not

- Object-oriented — no classes, inheritance or polymorphism
- Functional — no lambdas, closures or higher-order functions
- Self-extending — a BUBAS program cannot define functions, types or statements

---

## 2. Core Principles

**1 · Layered responsibility.** Infrastructure logic belongs in Java. When a BUBAS feature starts
to feel necessary, the correct move is almost always to add a Java function instead.

**2 · No user-defined functions.** All callable logic comes from the Java function registry.

**3 · No user-defined data structures.** Domain objects are declared in Java and travel through
BUBAS as values of a registered opaque type, which BUBAS can hold and pass but never inspect.

**4 · Single-line statement patterns.** A custom statement matches exactly one logical line.
Block structures are hardcoded in the core parser and cannot be extended.

**5 · One global scope.** There are no local scopes, no shadowing and no parameter passing
inside BUBAS, because there is nothing to pass parameters to.

**6 · Static safety is worth friction.** Definite assignment, unreachable code, unused variables,
lookalike identifiers, arithmetic overflow and pattern ambiguity are all rejected. A script that
compiles should not fail for a reason its author could have been told about earlier.

**7 · Anything requiring interpretation of an opaque value is the embedder's job.** BUBAS cannot
convert one to text, compare two of them, or test one for absence. An integration that needs
such an operation exposes it as a domain-named function. This single rule answers a whole family
of questions that would otherwise each need a language feature.

---

## 3. Architecture

BUBAS separates definition, compilation and execution into three objects with distinct lifetimes.

```
   BubasLanguage.builder()          mutable, single-threaded
        │  defineOpaqueType / defineFunction / defineStatement
        │  registerService (singletons)
        ▼
   .seal()  ──────────────────►  BubasLanguage
        │                        immutable · thread-safe · analysis done once
        │                        reserved-word set fixed
        │  compile(source)
        ▼
   BubasProgram                   immutable · reusable · fully checked
        │  Interpreter.of(program)
        ▼
   Interpreter                    cheap · single-use · single-threaded
        │  argument() / registerService() / mathContext()
        │  run()
        ▼
   Value
```

### Why three layers

Registration is expensive: pattern compilation, overlap analysis and the opaque-type lattice are
computed once, at `seal()`. Every `Interpreter` forked afterwards inherits that work. Compilation
is expensive too, so a `BubasProgram` is reusable across runs. Only the `Interpreter` is per-run,
and it is deliberately cheap.

**Nothing is resolved by name at run time.** Because implementations are named classes, the
compiler resolves every call while it analyses: an AST node carries the implementation instance,
the target method, and the `Class` behind each opaque type it checks. The registries are therefore
compile-time structures only. What survives into the run is data rather than lookup — the
language-level services and the default `MathContext` — and it travels with the compiled program.

This is why `BubasLanguage` and `BubasProgram` belong to the analyser and `Interpreter` to the
runtime: the runtime depends on the analyser, never the reverse, and execution is entered through
`Interpreter.of(program)` rather than a factory method on the program.

### Sealing

`seal()` closes registration permanently. From that moment the reserved-word set is fixed, so a
later registration could invalidate an already-compiled program; registering after seal throws.
At seal the implementation verifies that:

- no function name, opaque type name or pattern keyword collides with another, case-insensitively
- no pattern begins with a structural keyword
- placeholder names are unique within each pattern
- type references in patterns resolve
- no two patterns can match the same line (unless overlap analysis is disabled)

### Concurrency

A sealed `BubasLanguage` and a `BubasProgram` are immutable and safe to share across threads.
An `Interpreter` is not thread-safe and executes exactly one program, once. Concurrent
orchestration means one `Interpreter` per thread, all sharing one `BubasProgram`.

### Components

| Component | Purpose |
|-----------|---------|
| Lexer | Physical lines → tokens and logical lines; handles comments and continuation |
| Pattern matcher | Matches a logical line against the registered patterns |
| Parser | Block structures, expressions, function calls → AST |
| Symbol table | Variable declarations, types and states |
| Analyser | Type checking, definite assignment, reachability, use checking |
| Interpreter | Executes the AST |
| Function dispatcher | Evaluates arguments, invokes Java implementations |
| Code generator | Compiles the AST to Java source (Phase 3) |

---

## 4. Lexical Structure

### 4.1 Comments

A comment begins with an apostrophe and runs to the end of the **physical** line.

```basic
' A whole-line comment
DECLARE x INTEGER        ' an end-of-line comment
```

An apostrophe inside a string literal does not start a comment.

### 4.2 Logical lines and continuation

The lexer joins physical lines into **logical lines**. Everywhere else in this specification,
"line" means a logical line. A physical line is continued when any of the following holds:

- a `(` or `[` opened on it is still unclosed
- it ends with a binary operator or a comma — neither can legally end a statement
- it ends with an underscore `_`

```basic
total = subtotal +
            tax +
            shipping

x = COMPUTE(alpha,
                beta)

first = names[index +
                  offset]

VALIDATE order _
    AGAINST rules
```

Rules:

- an underscore inside a string literal is an ordinary character, not a continuation
- a trailing comment on an intermediate physical line is stripped before joining
- a string literal never spans a line break
- when a bracket is never closed, the diagnostic names the line where the bracket **opened**
- a lone `_` at the end of a line is a continuation marker, never a reference to a variable
  actually named `_`
- a lexical error is the one diagnostic that names a **physical** line, since that is where the
  author must look; every later stage reports logical lines

A line ending with `NOT`, `TO`, `THEN` or any other keyword that cannot legally end a statement
does **not** continue. Only a binary operator, a comma, an open bracket or an explicit `_` does.

This is deliberate, and the reason is worth stating because the alternative looks like an
improvement. The lexer could be taught which keywords may end a statement, and technically that
would work — but it would be the grammar's syntactic knowledge seeping down into the lexical
layer. Layers that leak into one another produce rules a reader cannot predict from either layer
alone, and the usability cost lands on exactly the audience this language exists for. Requiring an
explicit `_` keeps each layer's rule statable on its own:

```basic
IF NOT _
   ORDER_WAS_FOUND(purchase) THEN

FOR i = 0 TO _
    LENGTH(items) - 1
```

### 4.3 Keywords

Keywords are case-insensitive; `IF`, `if` and `If` are the same word. The core keyword set is
listed in [§15](#15-reserved-words). Registered command keywords — the literal words of a
statement pattern — are keywords too, and are equally case-insensitive.

The lexer does not classify them, and cannot. The reserved-word set is not fixed until `seal()`,
and it includes every literal token of every registered pattern, every function name and every
opaque type name. So the lexer emits every word-shaped token alike and the analyser classifies
against its registries — the same layering rule as the `NOT` case above. The sole exception is
`AND`, `OR` and `MOD`, which the continuation rule must recognise; those are core and can never
be extended, so knowing them costs the lexer no coupling.

### 4.4 Names

Variable names, function names and opaque type names start with a letter or underscore and
continue with letters, digits and underscores. "Letter" means any Unicode letter, not only ASCII:
the audience is subject matter experts, whose domain vocabulary is not necessarily English.

Names are **unique case-insensitively** and **written exactly as declared or registered**. A
declaration reserves the name in every casing; a later reference must match it character for
character. This rules out lookalike pairs and capitalisation typos in one rule.

```basic
DECLARE userId INTEGER
DECLARE UserID STRING    ' error: collides with userId
UserId = 5           ' error: declared as 'userId'
userId = 5           ' correct
```

All names share one namespace with all keywords. A variable may not be named after a core
keyword, a pattern keyword, a registered function or a registered opaque type.

### 4.5 Literals

```basic
42        0        -10        1000000            ' INTEGER
3.14      0.5      -2.71828   1000.0             ' DECIMAL
"Hello"   "Line 1\nLine 2"    "He said \"Hi\""   ' STRING
TRUE      FALSE                                  ' BOOLEAN
```

String escapes: `\n`, `\t`, `\r`, `\\`, `\"`.

### 4.6 Punctuation

Any character that is not whitespace, a name character, a digit, a quote or an apostrophe is a
single-character punctuation token. The lexer does not decide whether it belongs where it was
written — the parser does, and can say so far more precisely than "unexpected character" ever
could.

That generosity is what lets a statement pattern be lexed by this same lexer, braces and all,
rather than preprocessed into something a stricter lexer would accept. Brace pairs are balanced
like the others, so an unclosed `{` in a pattern is reported by the lexer rather than by
hand-written scanning.

A leading `-` is not part of a literal — `-10` is the unary operator applied to `10`, so `a-10`
and `a - 10` tokenize identically. An integer literal that does not fit in 64 bits is rejected
where it is written.

---

### 4.7 Lossless lexing

Every character of the source lands either in a token or in a piece of **trivia** — whitespace, a
comment, a continuation underscore, or a line terminator. Concatenating a logical line's trivia,
then each token's text and trailing trivia in order, reproduces the source exactly. Tooling that
needs comments, indentation and line structure — a language server above all — reads them from
the same scan the compiler used, rather than from a second scanner that could disagree with it.

Trivia has exactly one owner:

- everything between two tokens belongs to the **earlier** token
- everything before the first token of a line belongs to the **line**
- a line terminator belongs to the line it ends, so a logical line is self-contained

With *n* tokens there are *n+1* gaps and *n+1* slots, so no run of text is claimed twice and none
is left unclaimed. Attaching both leading and trailing trivia to tokens, as some compiler
frameworks do, gives every gap two plausible owners and needs a tie-break rule nobody remembers.

A blank or comment-only line is a logical line with **zero tokens** that owns all of its trivia.
That is why nothing needs a file-level trivia slot: trailing blank lines and a final comment block
are simply more zero-token lines. The parser skips them.

---

## 5. Type System

### 5.1 Types

| Type | Java representation | Array default | Notes |
|------|--------------------|---------------|-------|
| `INTEGER` | `long` / `Long` | `0` | 64-bit signed |
| `DECIMAL` | `java.math.BigDecimal` | `0` | arbitrary precision |
| `STRING` | `java.lang.String` | `""` | never null |
| `BOOLEAN` | `java.lang.Boolean` | `FALSE` | |
| *registered opaque type* | the registered Java class | `null` | opaque to BUBAS |

There is no `OPAQUE` keyword in BUBAS source. An opaque type is written by the name it was
registered under, and that name becomes a reserved word.

```java
builder.defineOpaqueType("Order", Order.class)
       .defineOpaqueType("Customer", Customer.class);
```

```basic
DECLARE purchase Order
DECLARE buyer Customer
```

A registered type name is reserved like any other, so **a variable may not be named after its
type**. `DECLARE order Order` is rejected — which is worth knowing in advance, because naming a
variable after its type is the first thing most people reach for. It is the same rule that bans
`userId` beside `UserID`, and the diagnostic says so rather than merely reporting a reserved word:

```
line 3: 'order' collides with the opaque type 'Order';
        a variable may not share a name with a type
    DECLARE order Order
```

### 5.2 Absence

BUBAS has no `NULL` literal, no null test and no null-producing operation. `null` remains an
ordinary Java value: an opaque array element starts as `null`, and a Java function may return
`null` for an opaque result. BUBAS holds such a value and passes it on without ever looking at
it. A script that must branch on absence uses a function the embedder supplies:

```basic
IF ORDER_WAS_FOUND(purchase) THEN
```

### 5.3 Arrays

An array is declared with its size between the name and the element type. The size is any
`INTEGER` expression, evaluated once when the declaration executes.

```basic
DECLARE numbers[5] INTEGER
DECLARE items[COUNT_ORDERS()] Order
DECLARE names[n * 2] STRING
```

- Indices are zero-based; an out-of-range index is a runtime error
- Arrays are always initialized: every element holds its type's default
- Arrays are one-dimensional; there are no arrays of arrays
- An array may not be FINAL, and array elements can never be final
- `LENGTH(a)` returns the size
- An array has no expression type: `x = numbers` is an error. An array may appear as a
  bare argument in a function call, and nowhere else

```basic
SORT_ITEMS(items)        ' legal: bare array name as an argument
copy = items         ' error: an array is not an expression
```

### 5.4 Assignability

A value of type `S` may be assigned to, or passed as, a target of type `T` when:

- `S` and `T` are the same type, or
- `S` is `INTEGER` and `T` is `DECIMAL`, or
- `S` and `T` are opaque types and `S`'s Java class is assignable to `T`'s

Opaque assignability follows Java, interfaces included, and is computed once at `seal()`.
Nothing else converts implicitly. There is no narrowing and no cast.

### 5.5 Type vocabulary outside BUBAS source

`NUMBER`, `ARRAY` and `VOID` never appear in BUBAS source. `NUMBER` and `ARRAY` are pattern
constraint vocabulary ([§9.4](#94-constraints)); `VOID` is a Java-side function return type.

---

## 6. Expressions

### 6.1 Operators and precedence

Highest to lowest:

1. `(` `)`
2. unary `NOT`, `-`, `+`
3. `*` `/` `MOD`
4. `+` `-`
5. `=` `<>` `<` `>` `<=` `>=`
6. `AND`
7. `OR`

All binary operators are left-associative.

### 6.2 INTEGER arithmetic

- `/` truncates toward zero: `7 / 2` is `3`, `-7 / 2` is `-3`
- `MOD` takes the sign of the dividend: `-7 MOD 2` is `-1`
- Division or `MOD` by zero is a runtime error
- Overflow is a runtime error, never a wraparound

### 6.3 DECIMAL arithmetic

`+`, `-` and `*` are exact. `/` uses the interpreter's `MathContext`, which defaults to
`MathContext.DECIMAL128` (34 digits, `HALF_EVEN`) and may be changed at runtime by a Java
function. Consequently:

- the same source may produce different results across runs, by design
- there is **no compile-time constant folding**, of division or of anything else
- generated Java reads the `MathContext` from the runtime rather than baking it in

### 6.4 Comparison

| Operand types | Legal operators |
|---------------|-----------------|
| `INTEGER`, `DECIMAL`, mixed | all six |
| `STRING` | all six, lexicographic by code point |
| `BOOLEAN` | `=` `<>` only |
| opaque | none |

`DECIMAL` equality compares numeric value, not representation: `2.0 = 2.00` is `TRUE`. Mixed
`INTEGER`/`DECIMAL` comparison widens the integer first.

### 6.5 String concatenation

`+` concatenates when the **left** operand is a `STRING`, coercing the right operand. It never
coerces the left operand.

```basic
"Count: " + 42        ' "Count: 42"
42 + " items"         ' error: cannot add STRING to INTEGER
"" + 42 + " items"    ' "42 items"
```

Because `+` groups left, `"n=" + 1 + 2` is `"n=12"`, not `"n=3"`.

Rendering: `INTEGER` in plain digits; `DECIMAL` in plain notation, never scientific, preserving
scale (`10.50` renders as `10.50`); `BOOLEAN` as `TRUE` or `FALSE`, matching the literals. An
opaque value cannot be coerced to a string at all — the embedder supplies a domain-named
function if one is wanted.

### 6.6 Function calls in expressions

Parentheses are mandatory in an expression. A function used in an expression must not return
`VOID`.

```basic
purchase = LOAD_ORDER(orderId)
IF VALIDATE_ORDER(order) AND IS_URGENT(order) THEN
```

---

## 7. Statements

### 7.1 Program structure

A source file contains exactly one program. Its name is documentation and must be a valid name.

```basic
PROGRAM ProcessOrder(orderId INTEGER, region STRING) RETURNS BOOLEAN
    ...
END.
```

- The program block is closed by `END`, optionally followed by `.`
- `END` is never a statement; it terminates blocks only
- Parameters are supplied by the embedder before the run and are `FINAL` and `INITIALIZED`
  on entry — a program cannot rebind its own inputs
- Both the parameter list and the `RETURNS` clause are optional
- With a `RETURNS` clause, every path must return a value of an assignable type
- Without one, `RETURN` takes no value and `run()` yields no value
- Newline separates statements; `;` is not a separator

A header spanning several lines follows the ordinary continuation rules:

```basic
PROGRAM ProcessOrder(orderId INTEGER,
                     region STRING) RETURNS BOOLEAN
```

### 7.2 Declarations

```basic
DECLARE count INTEGER
DECLARE total DECIMAL = 0.0
DECLARE rate DECIMAL FINAL = 0.07
DECLARE numbers[5] INTEGER
DECLARE items[COUNT_ORDERS()] Order
```

A `FINAL` variable requires an initializer and can never be reassigned. Finality is not a state
a variable enters later: what is final is final from its declaration.

### 7.3 Assignment

Assignment has no keyword. BUBAS is not BASIC; `LET` faded out of the languages that had it, and
no language written since asks for it on the most frequent line in every script.

```basic
count = 0
numbers[i] = numbers[i] + 1
```

The target must be declared and not final. The value must be assignable to the target's declared
type. Assignment is the one built-in whose pattern carries no keyword at all, which is why a
pattern is not required to begin with one — see [§9.1](#91-matching).

### 7.4 Conditionals

```basic
IF score >= 90 THEN
    grade = "A"
ELSEIF score >= 80 THEN
    grade = "B"
ELSE
    grade = "C"
END IF
```

`ELSEIF` is one word; `ELIF`, `ELSIF` and a two-word `ELSE IF` are not accepted. Because
statements are line-based, `ELSE IF x THEN` is two statements on one line and is rejected
without needing a special rule.

### 7.5 Loops

```basic
DO WHILE count < 10          DO
    count = count + 1            count = count + 1
END DO                       END DO UNTIL count >= 10

DO UNTIL done                DO
    done = STEP_DONE()           done = STEP_DONE()
END DO                       END DO WHILE NOT done
```

The condition may sit at either end. At the top it is tested before each pass, so the body may
never run. At the bottom it is tested after each pass, so the body always runs at least once —
which is what lets a post-test loop satisfy a definite-assignment obligation.

```basic
FOR i = 0 TO LENGTH(items) - 1
    PROCESS_ITEM(items[i])
END FOR

FOR i = 10 TO 0 STEP -2
    LOG_EVENT("INFO", "" + i)
END FOR
```

- The loop variable must already be declared `INTEGER` and must not be final
- Start, end and step are each evaluated once, on entry
- The body may not assign the loop variable
- A zero step is a runtime error
- After the loop the variable is `INITIALIZED` and holds the first value that failed the test

```basic
FOR i = 0 TO 100
    IF MATCHES(items[i]) THEN
        hit = i
        EXIT FOR
    END IF
END FOR
```

`EXIT FOR` and `EXIT DO` each leave the innermost enclosing loop **of that kind**; there are no
labels. Using one with no enclosing loop of that kind is a compile error.

### 7.6 Return

```basic
IF NOT VALIDATE_ORDER(order) THEN
    LOG_EVENT("ERROR", "invalid order")
    RETURN FALSE
END IF
RETURN TRUE
```

`RETURN` may appear anywhere, including inside loops, and terminates the program.

### 7.7 Statement-form calls

A `VOID` function may be called as a statement, with or without parentheses. A function that
returns a value may **not** be called as a statement, so a result can never be discarded
silently.

```basic
LOG_EVENT "INFO", message      ' legal, LOG_EVENT is VOID
LOG_EVENT("INFO", message)     ' also legal
LOAD_ORDER 42                  ' error: LOAD_ORDER returns a value
```

---

## 8. Variable State and Static Analysis

### 8.1 Two axes

Variable state is a pair, not a single enum.

| Axis | Values |
|------|--------|
| Assignment | `UNDECLARED` → `DECLARED` → `INITIALIZED` |
| Mutability | `MUTABLE` or `FINAL`, fixed at declaration |

`FINAL` implies `INITIALIZED` at the point of declaration. A variable never becomes final later,
and never becomes uninitialized.

### 8.2 Definite assignment

Reading a variable requires it to be `INITIALIZED` on every path reaching that point.

- A statement initializes a variable when its pattern's postcondition says so
- **IF chain**: initialized afterwards only if every branch initializes it *and* the chain ends
  with `ELSE`
- **Pre-test loop, FOR loop**: the body may not run, so it guarantees nothing
- **Post-test loop**: the body runs at least once, so unconditional assignments in it count
- **FOR loop variable**: assigned on entry, so it is `INITIALIZED` after the loop either way
- **RETURN, EXIT**: abrupt; such paths do not contribute to the merge at the join point
- **An indexed target changes nothing.** `a[i] = 5` does not make `a` initialized, because it
  already was: an array is fully initialized at its declaration

### 8.3 Rejected at compile time

- reading a variable that is not definitely initialized
- assigning a `FINAL` variable, or a program parameter
- redeclaring an existing name, or declaring one that collides case-insensitively
- unreachable code: after `RETURN`, after `EXIT`, or after a provably non-terminating loop
- a declared variable that is never read
- a path that reaches the end of a program declaring `RETURNS` without returning a value
- any type or assignability violation
- a line matching more than one pattern, or matching none

---

## 9. The Pattern System

A statement pattern is a single-line syntax definition registered from Java. It tells the
compiler how to recognise a statement, what it requires of the variables it mentions, and what
it guarantees afterwards.

### 9.1 Matching

A pattern matches a whole logical line, comment already stripped. Literal words in the pattern
match case-insensitively; placeholders capture the rest.

**Every literal token in every pattern becomes a reserved word.** Registering
`PAY {expression:amount} VIA {var:account}` reserves both `PAY` and `VIA`. This is what makes
expression boundaries decidable without backtracking: an expression ends at the first reserved
token, so `PAY a + b VIA acct` splits unambiguously.

If a line matches two patterns, that is a compile error. Overlap is also checked at `seal()` by
approximating each pattern as a regular language over token classes and testing pairwise
intersection — the emptiness test for the intersection of two regular languages, over a product of
their automata. Every colliding pair is reported at once, so an embedder registering a vocabulary
sees all of its conflicts in one go rather than one per attempt.

The check has to run at `seal()` and not before: whether `PAY {expression:a} VIA {var:b}` and
`PAY {expression:a} FROM {var:b}` collide depends on `FROM` being reserved by the other pattern,
which is not known until every pattern is registered.

The approximation errs towards warning — an expression is modelled as one-or-more expression
tokens with no bracket structure, so a pair may be reported that no real line could hit, but no
colliding pair can slip through. `skipOverlapAnalysis(true)` disables it, both for startup cost in
production and for grammars whose author knows better.

A pattern need not begin with a keyword. The built-in assignment begins with a placeholder and its
only literal is `=`, so requiring a leading word would make the most frequent statement in the
language inexpressible. `{var:x} IS SET` is a perfectly good pattern.

What a pattern must have is **at least one literal**, of any kind. A pattern made only of
placeholders reserves nothing, is invisible to the reserved-word mechanism, and matches by shape
alone; `{var:a} {var:b}` is rejected.

The matcher therefore tries every registered pattern rather than pre-filtering on a first word.
That costs nothing at the scale a language vocabulary reaches, and the reserved-word rule does
most of the narrowing anyway: a pattern beginning with `{expression:e}` cannot match
`VALIDATE order AGAINST rules`, because an expression cannot begin at a reserved token.

When a line matches nothing, the diagnostic depends on what can be said. If its first token is a
reserved word beginning one or more patterns, it names them; otherwise it reports an unknown
statement.

```
line 12: PAY does not match its pattern
    PAY {expression:amount} VIA {var:account}

line 17: unknown statement FOO
```

A pattern may not begin with a structural keyword — `PROGRAM`, `IF`, `ELSEIF`, `ELSE`, `DO`,
`WHILE`, `UNTIL`, `FOR`, `EXIT`, `RETURN`, `END` — because those drive block parsing rather than
line matching. Beginning with an existing pattern keyword such as `DECLARE` is allowed if
overlap analysis passes, so an embedder may add a declaration variant but can never displace a
built-in. In practice, choose a fresh word: `WHEN`, not `IF`.

### 9.2 Placeholder syntax

```
{ prefixes > kind[/constraint] : name > postfixes }
```

Every zone is optional and spaces around `>` and `:` are ignored. A zone marker `>` appears only
when the zone beyond it is present.

```
{expression:amount}
{var:total}
{new > var/T:x}
{var:total > initialized}
{mutable:initialized > var:total > initialized}
{initialized > var/Order:o}
{expression/T:init}
{literal/INTEGER:times}
{type:T}
```

An unnamed placeholder takes its **kind** as its name: `{expression}` is named `expression`,
`{new > var/T}` is named `var`. Placeholder names must be unique within a pattern, which is why
at most one placeholder of each kind may be left unnamed. State keywords may not be used as
placeholder names, which is what makes `{var:total}` and `{var:initialized}` distinguishable.

### 9.3 Kinds

| Kind | Captures |
|------|----------|
| `var` | A reference to storage: a name, optionally followed by `[expression]` |
| `identifier` | A bare variable name, never indexed |
| `expression` | A full expression, evaluated lazily by the handler |
| `literal` | A literal, required to be a compile-time constant |
| `type` | A type designator |

**`var` and `identifier` differ in exactly one thing: appetite.** A `var` swallows an index if one
is there, so `ADD 5 TO totals[3]` matches `ADD {literal/NUMBER:n} TO {var:total}`. An `identifier`
never does, which is what a pattern needs when it supplies the brackets itself.

Only an `identifier` may be created. `{new > var:x}` is not merely a hole that fails to match a
name — it is a pattern that should never have been written, because `a[i]` is not a name, and
registration rejects it. For the same reason a `var` placeholder may not be immediately followed
by a literal `[`: given `a[1]`, nothing could say whether the hole took `a` or `a[1]`.

The static type of a `var` is the type of the *reference*, so an indexed one has the array's
element type. That is what lets a single assignment pattern serve both `x = 5` and `a[i] = 5`.

A `literal` placeholder constrained to `INTEGER`, `DECIMAL` or `NUMBER` accepts an optional `+` or
`-` before the number, and captures the signed value. The lexer deliberately does not produce
signed literals — `-10` is unary minus applied to `10`, so that `a-10` and `a - 10` tokenize alike
— so the sign is reassembled here, at the only layer that knows a constant is required.

```
ADD {literal/NUMBER:amount} TO {mutable:initialized > var:total > initialized}

ADD 50.50 TO total     ' matches
ADD -50.50 TO total    ' matches
ADD 3 - 5 TO total     ' no match: a literal is not an expression
```

### 9.4 Constraints

A constraint follows the kind after `/`.

```
{var/INTEGER:count}          ' an INTEGER variable
{var/Order:o}                ' a variable of opaque type Order
{var/ARRAY:a}                ' any array variable
{var/ARRAY/INTEGER:a}        ' an array of INTEGER
{expression/NUMBER:v}        ' INTEGER or DECIMAL
{literal/INTEGER:times}      ' an integer literal
```

`NUMBER` matches `INTEGER` or `DECIMAL`. An expression never has an array type.

**Type references.** A constraint may instead name another placeholder in the same pattern:

| Written | Means                                                                   |
|---------|-------------------------------------------------------------------------|
| `/T` where `T` is a `{type:T}` hole | the type actually written at that position                             |
| `/x` where `x` is a `{var:x}` hole | the static type of that reference — the element type when it is indexed |
| `/x` where `x` is an `{identifier:x}` hole | that variable's declared type                                           |
| `/e` where `e` is an `{expression:e}` hole | that expression's static type                                           |
| `/a[]` where `a` is array-typed | that array's element type                                               |

References resolve within the pattern regardless of position, so a placeholder may refer to one
declared later.

A reference means **assignment-compatible with**, so `DECLARE d DECIMAL = 5` is legal. Write
`/=T` to demand exactly the same type. An unresolvable name is a registration error.

### 9.5 Preconditions and postconditions

Prefixes are requirements checked before the statement; postfixes are guarantees the analyser
relies on afterwards. There are two independent prefix axes, written mutability first:

| Prefix | Requires |
|--------|----------|
| `new` | the name does not exist yet |
| `declared` | the variable exists |
| `initialized` | the variable has a value |
| `mutable` | the variable is not final |
| `final` | the variable is final |

| Postfix | Guarantees |
|---------|-----------|
| `declared` | the variable exists afterwards |
| `initialized` | the variable has a value afterwards |
| `final` | the variable is final, and therefore newly created and initialized |

Two axes are necessary. `ADD 50.50 TO total` requires `total` to be both readable and writable,
which a single prefix slot cannot express:

```
ADD {literal/NUMBER:amount} TO {mutable:initialized > var:total > initialized}
```

A placeholder with a `new` prefix **must carry a type constraint** — a concrete type, or a
reference to a `{type:T}` placeholder in the same pattern. Without one the analyser could not know
the type of the variable being created, and every later use of it would be uncheckable.
Registration rejects such a pattern.

```
FETCH {type:T} INTO {new > var/T:out > initialized}      ' FETCH Order INTO result
OPEN LEDGER {new > var/Ledger:handle > final}            ' type fixed by the pattern
FETCH INTO {new > var:out > initialized}                 ' registration error: no type
```

Because the type and the finality are settled by the pattern, the runtime creates the slot before
invoking the handler; the handler only supplies the value. See
[§10.8](#108-values-and-arguments).

Rules: `new` is a prefix only; a `final` postfix implies a `new` prefix; `final` cannot be
combined with a `declared` or `initialized` prefix; the postcondition of a custom statement is
**verified when its handler returns**, and a handler that fails to deliver what its pattern
promises raises an error at its own statement rather than corrupting the analyser's model.

### 9.6 Built-in patterns

The built-ins are ordinary patterns, and expand to core AST nodes rather than handler calls —
which is what allows them to be compiled to standalone Java.

```
DECLARE {new > identifier/T:name > declared} {type:T}
DECLARE {new > identifier/T:name > initialized} {type:T} = {expression/T:init}
DECLARE {new > identifier/T:name > final} {type:T} FINAL = {expression/T:init}
DECLARE {new > identifier/ARRAY/T:name > initialized}[{expression/INTEGER:size}] {type:T}

{mutable:declared > var:name > initialized} = {expression/name:value}
```

Three things to note.

**One assignment pattern serves both `x = 5` and `a[i] = 5`,** because a `var` absorbs an index
and the static type of an indexed reference is the element type. `{expression/name:value}` then
expresses, for the first time, that the right-hand side must match whatever the target actually
is.

**Each `DECLARE` variant constrains its creating placeholder by naming the `{type:T}` hole that
appears later in the same pattern.** Type references resolve within a pattern regardless of order,
so a forward reference is fine.

**On a creating placeholder the constraint is not a check.** `/T` is the type the runtime declares
the variable with; the handler neither chooses it nor supplies it. Everywhere else a constraint
validates what was written, but here it instructs.

### 9.7 Custom statements

A command is a class, exactly like a function — see [§10.1](#101-one-class-one-thing). Its single
public method takes the context followed by one parameter per placeholder, in pattern order.

```java
builder.defineStatement("VALIDATE {initialized > var/Order:item} AGAINST {expression:rules}",
                        Validate.class)
```

```java
public final class Validate {
    public void call(StatementContext ctx, VariableArg item, ExpressionArg rules) {
        Order order = item.get().as(Order.class);
        if (!ctx.service(RuleEngine.class).check(order, rules.evaluate().asString())) {
            ctx.error("validation failed for " + item.name());
        }
    }
}
```

```basic
VALIDATE order AGAINST rules
```

Placeholder kinds map to parameter types:

| Kind | Parameter type |
|------|----------------|
| `var` | `VariableArg` |
| `expression` | `ExpressionArg` |
| `literal` | its Java value directly — `long`, `BigDecimal`, `String`, `boolean` — or `Value` when unconstrained |
| `type` | `BubasType` |

This is the general division of labour: **a function receives its arguments evaluated; a command
receives expressions unevaluated and decides whether, and how often, to evaluate them.**

Expression placeholders are therefore **lazy**: evaluated when, and as often as, the handler asks.
That is what lets a custom statement express control flow rather than only side effects. The sole
expression with a cap is the index of an indexed `var` reference, for the reason given in
[§10.8](#108-values-and-arguments). Note that the
unnamed `{expression}` below is named `expression`, and the parameter matches:

```java
builder.defineStatement("EXECUTE {expression} TIMES {expression/INTEGER:n}",
                        ExecuteTimes.class)
```

```java
public final class ExecuteTimes {
    public void call(StatementContext ctx, ExpressionArg expression, ExpressionArg n) {
        long times = n.evaluate().asLong();
        for (long i = 0; i < times; i++) {
            expression.evaluate();
        }
    }
}
```

---

## 10. Java Integration

### 10.1 One class, one thing

Every function and every command is implemented by its own class. The runtime instantiates it
once per sealed language, through a public no-arg constructor or a public static `provider()`
method — deliberately the same contract `ServiceLoader` uses, so one class works through either
registration route.

The implementation is **the single public method the class declares**. Its name is irrelevant;
helpers are private. Declaring none, or more than one, fails at `seal()` naming the class, so a
stray public helper is a reported error rather than a silent substitution. Overrides of `Object`
methods are ignored.

Nothing anywhere names a method in a string. A class reference is what an IDE renames and the
compiler checks; a method name in a string literal is neither, and would rot silently under
refactoring.

To keep related implementations in one file, nest them:

```java
public final class OrderVocabulary {
    public static final class LoadOrder  { ... }
    public static final class OrderTotal { ... }
}
```

Because a `BubasLanguage` is shared across threads, an implementation class must be thread-safe;
in practice it should be stateless. It is constructed with no arguments, so it cannot capture the
embedder's objects the way a lambda could. Every dependency arrives through `ctx.service(...)`,
which makes the service registry the only path from a shared, sealed language to per-run state —
enforced by construction rather than by convention.

### 10.2 Signatures are derived from Java

A BUBAS signature is read off the Java method. The first parameter is always the context; the
rest are the BUBAS parameters, in order.

```java
public final class LoadOrder {
    public Order call(Context ctx, long orderId) {
        return ctx.service(OrderService.class).load(orderId);
    }
}
```

Registered as `LOAD_ORDER`, that declares `LOAD_ORDER(orderId INTEGER) -> Order`. There is one
signature per name and no overloading. Arguments are evaluated **eagerly**, in order, before the
implementation runs, and each must be assignable to its parameter type.

| BUBAS type | Java type |
|------------|-----------|
| `INTEGER` | `long` |
| `DECIMAL` | `java.math.BigDecimal` |
| `STRING` | `java.lang.String` |
| `BOOLEAN` | `boolean` |
| opaque `T` | the Java class registered for `T` |
| array of `INTEGER` | `long[]` |
| array of `DECIMAL` | `BigDecimal[]` |
| array of `STRING` | `String[]` |
| array of `BOOLEAN` | `boolean[]` |
| array of opaque `T` | `T[]` |
| any array | `BubasArray` |
| `VOID` (return position only) | `void` |

Three consequences follow.

**The opaque mapping is one-to-one.** Because a Java class identifies a BUBAS type, registering
two type names against the same class is a `seal()` error, where previously it was merely odd.

**An array is passed as the interpreter's backing store**, not a copy — no wrapper, no boxing, and
`Arrays.sort` and `Arrays.stream` work on it directly. Writes are visible to the script
immediately, which is what makes `SORT_ITEMS(items)` mean what it looks like. That does not
conflict with functions being unable to write variables: an element write cannot change a
binding, cannot violate finality since arrays are never final, and cannot change a type since the
element type is fixed at declaration. An implementation must not retain the reference after the
call returns.

**`BubasArray` exists only for the element-agnostic case.** `LENGTH` is its sole use in the
prelude:

```java
public interface BubasArray {
    int       size();
    BubasType elementType();
    Object    raw();            // the backing long[], String[], Order[], ...
}
```

Parameter names come from the Java parameter names when the class is compiled with `-parameters`,
and `@Param("orderId")` overrides them. Names are documentation only, since BUBAS calls are
positional, so a class compiled without the flag degrades to `arg0` rather than failing and
`seal()` reports it as a diagnostic.

### 10.3 Building a language

```java
BubasLanguage lang = BubasLanguage.builder()
    .defineOpaqueType("Order", Order.class)
    .defineOpaqueType("Customer", Customer.class)

    .registerService(Clock.class, systemClock)
    .registerService(DataSource.class, "read",  readOnlyDs)
    .registerService(DataSource.class, "write", primaryDs)

    .defineFunction("LOAD_ORDER", LoadOrder.class)
    .defineFunction("LOG_EVENT",  LogEvent.class)

    .defineStatement("VALIDATE {initialized > var/Order:item} AGAINST {expression:rules}",
                     Validate.class)

    .extensions()
        .classloader(pluginClassLoader)
        .filter(e -> e.getClass().getPackageName().startsWith("com.acme."))
        .register()

    .seal();
```

Every definition is one call returning the builder. There is no nested builder and no terminal
method, because the class carries everything a nested builder used to declare.

### 10.4 Compiling and running

```java
BubasProgram prog = lang.compile(source);

for (long id : orderIds) {
    Value result = Interpreter.of(prog)
        .argument("orderId", id)
        .argument("region", "EU")
        .registerService(Transaction.class, tx)
        .mathContext(MathContext.DECIMAL128)
        .run();

    if (result.asBoolean()) { ... }
}
```

`run()` may be called once per `Interpreter`; a second call throws.

### 10.5 Extensions and discovery

A function or command may instead be self-describing and discoverable by `ServiceLoader`. This is
how the optional prelude packages and third-party libraries are delivered.

```
BubasExtension                                    marker interface
  ├── BubasFunction extends BubasExtension
  └── BubasCommand  extends BubasExtension

AbstractBubasFunction implements BubasFunction    annotation harvesting
AbstractBubasCommand  implements BubasCommand     annotation harvesting
```

A concrete extension extends one of the abstract classes and carries an annotation. The abstract
base reads that annotation from the concrete class and supplies the function name or the
statement pattern; the registrar switches on `instanceof` and routes each to the matching
registration. Everything else — the implementing method, the derived signature, instantiation —
works exactly as in [§10.1](#101-one-class-one-thing) and [§10.2](#102-signatures-are-derived-from-java).

```java
@BubasFunction("LOAD_ORDER")
public class LoadOrder extends AbstractBubasFunction {

    public Order call(Context ctx, long orderId) {
        return ctx.service(OrderService.class).load(orderId);
    }

    private static String cacheKey(long id) { ... }
}

@BubasCommand("VALIDATE {initialized > var/Order:item} AGAINST {expression:rules}")
public class Validate extends AbstractBubasCommand {

    public void call(StatementContext ctx, VariableArg item, ExpressionArg rules) { ... }
}
```

The only difference between the two routes is where the name or pattern comes from: the builder
call supplies it for a plain class, the annotation supplies it for a discoverable one.

#### Registration

`ServiceLoader` matches the exact service type requested, so providers must be declared for the
marker rather than for a subtype:

```java
module bubas.support {
    requires bubas.api;
    provides org.bubas.api.BubasExtension with LoadOrder, Validate;
}
```

Declaring `provides BubasFunction with LoadOrder` would not be found by
`ServiceLoader.load(BubasExtension.class)`, and would make every new SPI subtype a breaking change
for extension authors.

Discovery is not filtered — `ServiceLoader` finds whatever the classpath holds. **Registration** is
what the embedder controls, through the selector shown in [§10.3](#103-building-a-language). With
no `classloader` the context class loader is used; with no `filter` everything discovered is
registered.

Registration is deliberately opt-in. A registered extension contributes reserved words, so
automatic registration would let an unrelated jar appearing on the classpath reserve a word an
existing script uses as a variable name, breaking it with no change to either the script or the
embedding code.

### 10.6 Types

```java
BubasType.INTEGER
BubasType.DECIMAL
BubasType.STRING
BubasType.BOOLEAN
BubasType.VOID
BubasType.opaque("Order")
BubasType.arrayOf(BubasType.INTEGER)
BubasType.ANY_ARRAY
```

`BubasType` is mostly an introspection type now that signatures are derived: it surfaces through
`Value.type()`, `BubasArray.elementType()`, `ctx.type(name)` in a statement handler, and the
Phase 4 vocabulary export. `ANY_ARRAY` is only ever a parameter type — never a variable type, a
return type or a constraint.

### 10.7 Contexts

**A function cannot touch the variable store at all.** Its arguments arrive as typed method
parameters and it returns a value; that is the whole interface. Only a statement handler may read
or modify variables, and only as its pattern's preconditions and postconditions declare — which is
why it gets a richer context.

```java
public interface Context {
    <T> T service(Class<T> type);
    <T> T service(Class<T> type, String qualifier);
    MathContext mathContext();
    void log(String level, String message);
    void debug(String message);
    void error(String message);                     // throws BubasException
}

public interface StatementContext extends Context {
    ExpressionArg expression(String name);
    VariableArg   variable(String name);
    LiteralArg    literal(String name);
    BubasType     type(String name);
}
```

A function reading a global by name would be a use the definite-assignment analysis never
observes, on a value whose type nothing checked, possibly before it was ever assigned. There is no
accessor for it.

Service lookup walks the interpreter first, then the language, so a run-scoped service overrides a
singleton of the same key. The key is the class plus an optional qualifier, defaulting to the
empty string.

Services are invisible to BUBAS. No syntax refers to them and no script can observe one; they
exist so that shared implementations in a sealed language can reach per-run state such as a
transaction, a tenant or a correlation id.

#### Ambient configuration

State that many functions share — a format pattern, the digit count for loan arithmetic, a
mandator id — is a service, not a variable. A function or command sets it, and everything else
reads it from there:

```basic
PROGRAM Loans
    SET_LOAN_DIGITS 4
    rate = LOAN_INTEREST(principal)
END.
```

```java
public final class SetLoanDigits {
    public void call(Context ctx, long digits) {
        ctx.service(LoanConfig.class).setDigits((int) digits);
    }
}
```

`LoanConfig` is registered per interpreter, so the setting is scoped to the run and cannot leak
between concurrent orchestrations. This is the layering principle applied: state shared across
implementations belongs in Java, not threaded through every call site in the script.

### 10.8 Values and arguments

```java
public interface Value {
    BubasType  type();
    long       asLong();
    BigDecimal asDecimal();
    String     asString();
    boolean    asBoolean();
    <T> T      as(Class<T> javaType);      // checked against the registered opaque type
}

public interface ExpressionArg {
    Value     evaluate();                  // may be called any number of times
    BubasType staticType();
}

public interface VariableArg {
    String     name();                     // the script variable's name, for diagnostics
    BubasType  type();                     // the reference's type: element type when indexed
    boolean    isFinal();                  // resolved: open when no mutability prefix is given
    boolean    isIndexed();
    ArrayIndex index();                    // fails unless isIndexed(); always the same instance
    Value      get();
    void       set(Value value);
    void       set(Object javaValue);
}

public interface ArrayIndex {
    void evaluate();                       // once; a second call throws
    long get();                            // fails unless evaluate() has been called
}

public interface LiteralArg {
    Value value();
}
```

`as(Class)` is checked against the registered opaque type rather than blind-casting, so a
mismatch produces a BUBAS diagnostic instead of a `ClassCastException`.

#### An indexed reference

When a `var` placeholder matched `A[5]`, the index arrives **unevaluated**, like every other
expression a command receives. It is the one expression with a cap: **at most once**. The reason
is specific to what it does — it selects *which* location is read or written, so a second
evaluation could pick a different element and leave `get()` and `set(...)` disagreeing about what
they touched. It may also have side effects, and a command may legitimately never need it.

An index is always `INTEGER`, so `ArrayIndex` deals in `long` directly: there is no `Value` to
unwrap and no `asLong()` to write.

```java
public void call(StatementContext ctx, VariableArg target) {
    target.index().evaluate();                      // once
    ctx.log("INFO", "clearing slot " + target.index().get());
    target.set(0L);
}
```

`get()` and `set(...)` may be used **only after** `evaluate()` has been called, and `evaluate()`
throws on a second call. There is no memoisation and no hidden evaluation.

A handler may ignore a `var` placeholder completely — leave its index unevaluated and never read
or write the location. That is ordinary laziness, and it means the index expression's side effects
never happen at all. Given

```basic
SELECT 2 FROM A[4] AND B[4]
```

a handler that needs only the second source evaluates only `B`'s index; `A`'s never runs. What a
handler may not do is touch a location without having evaluated the index that selects it.

**There is no `get(index)` or `set(index, value)`.** A `var` names one location and can reach no
other. Given `MODIFY A[5]`, the handler alters `A[5]`; it has no way to reach `A[6]`, and that is
not an oversight but the guarantee the script author reads off the line.

#### A whole array

An array-typed placeholder is the opposite case and is unrestricted. `RESET A FROM 3 TO 7` matches
`{var/ARRAY:a}` with no index at all, so `get()` yields the array itself — the interpreter's
backing store, as [§10.2](#102-signatures-are-derived-from-java) describes. The handler may write
elements 3 through 7, or every element, or none. Nothing in the line promised otherwise.

The difference is visible in the source: `A[5]` names one slot, `A` names the array.

`VariableArg` is deliberately small, because the pattern has already decided almost everything.

**There is no `declare()`.** A placeholder that creates a variable must carry a type constraint,
so the runtime creates the slot itself before invoking the handler. Declaring is the framework's
job; only the value needs the handler.

**`type()` and `isFinal()` survive because a pattern may leave them open.** A `/NUMBER` constraint
admits `INTEGER` or `DECIMAL`; an omitted mutability prefix admits a final variable and a mutable
one alike. Neither can be resolved by splitting the pattern in two, because
`X {final > var:a}` and `X {mutable > var:a}` have identical token shapes and overlap analysis
would reject the pair — so asking at runtime is the only option available.

**There is no `isInitialized()`.** The analyser already governs it: a handler may read exactly
where the pattern declared an `initialized` precondition, and where it writes, what was there
before does not matter.

| Postcondition | What the runtime does | What the handler must do |
|---------------|----------------------|--------------------------|
| `> declared` | creates the slot, uninitialized | nothing |
| `> initialized` | creates the slot | `set(...)` |
| `> final` | creates the slot, sealing after the first write | `set(...)` exactly once |

Only an `identifier` placeholder creates anything; the type it is declared with comes from the
placeholder's constraint.

Whether the handler did its part is verified when it returns.

---

## 11. Errors

Every failure aborts the run. BUBAS has no error handling construct, no recovery and no
`ON ERROR`: error policy belongs to the embedding application, in Java.

```java
try {
    Value result = Interpreter.of(prog).argument("orderId", 42L).run();
} catch (BubasException e) {
    e.getLine();        // 14
    e.getSourceLine();  // "x = a / b"
    e.getMessage();     // "division by zero"
    e.getCause();       // the underlying Java exception, if any
}
```

`ctx.error(message)` throws a `BubasException`; it is a control-flow operation, not a logging
call. Anything thrown by a Java function is wrapped with the line information of the statement
that called it.

### Definition errors

A `BubasException` always points at a line of BUBAS source and is aimed at the script author.
Mistakes in how the embedder *defined* the language are a different category, raised at
registration or at `seal()` rather than at compilation, and read by a different person. They are
`BubasDefinitionException`: a malformed pattern, an implementation class whose signature does not
match its declaration, a name collision, two patterns that could match one line. It carries no
line number, because there is no source to point at — it names the pattern or the class instead.

```
in pattern "FETCH INTO {new > var:out > initialized}": 'out' creates a variable
and so must carry a type constraint, otherwise nothing could type its later uses
```

Compilation failures are reported before any execution, as a list, so a script author sees every
problem at once rather than one per attempt.

---

## 12. Standard Prelude

Every sealed language automatically registers exactly the functions the specification itself
depends on, and nothing more:

```
TO_INTEGER(s STRING) -> INTEGER
TO_DECIMAL(s STRING) -> DECIMAL
LENGTH(a ANY_ARRAY)  -> INTEGER
```

There is no `TO_STRING`: `"" + x` already converts, and a second way to do one thing invites
inconsistency. There is no null test, because there is no null in the language.

Larger vocabularies — string manipulation, regular expressions, date handling, mathematics —
ship as optional packages installed explicitly (Phase 2):

```java
BubasStrings.installInto(builder);
BubasRegex.installInto(builder);
```

---

## 13. Code Generation

A `BubasProgram` can be compiled to Java source. Because every function and statement
implementation is a named static method rather than a lambda, generated code calls them
directly:

```java
// generated
private final LoadOrder fn_loadOrder = new LoadOrder();
private final Validate  cmd_validate = new Validate();
...
Order order = fn_loadOrder.call(ctx, orderId);
cmd_validate.call(ctx, v_order, rulesThunk);
```

The output needs the implementation classes and the BUBAS runtime on the classpath, and nothing
else — the registration code does not have to run first. Had implementations been lambdas, every
call would have dispatched through the registry by string key, and the compiled program would
have been unable to run without first re-executing the whole builder.

The runtime is still required, for `DECIMAL` division reading the interpreter's `MathContext` at
each site, for overflow, division-by-zero and bounds checks emitted inline, for postcondition
verification after a custom statement, and for the expression thunks that lazy statement
placeholders need.

The generated code and the interpreter are two implementations of one semantics, so **a conformance
suite is mandatory**: every test program must run through both backends and produce identical
results and identical errors. Divergence between the two is the characteristic bug of this
design, and only the suite will catch it.

---

## 14. Worked Example

```java
BubasLanguage lang = BubasLanguage.builder()
    .defineOpaqueType("Order", Order.class)

    .defineFunction("LOAD_ORDER",      OrderVocabulary.LoadOrder.class)
    .defineFunction("ORDER_WAS_FOUND", OrderVocabulary.OrderWasFound.class)
    .defineFunction("ORDER_TOTAL",     OrderVocabulary.OrderTotal.class)
    .defineFunction("LOG_EVENT",       LogEvent.class)

    .registerService(OrderService.class, orderService)
    .seal();
```

```java
public final class OrderVocabulary {

    public static final class LoadOrder {
        public Order call(Context ctx, long orderId) {
            return ctx.service(OrderService.class).load(orderId);
        }
    }

    public static final class OrderWasFound {
        public boolean call(Context ctx, Order order) {
            return order != null;
        }
    }

    public static final class OrderTotal {
        public BigDecimal call(Context ctx, Order order) {
            return order.total();
        }
    }
}
```

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

    LOG_EVENT "INFO", "approved, total " + total
    RETURN TRUE
END.
```

```java
BubasProgram prog = lang.compile(source);

boolean approved = Interpreter.of(prog)
    .argument("orderId", 42L)
    .argument("limit", new BigDecimal("1000.00"))
    .run()
    .asBoolean();
```

---

## 15. Reserved Words

### Core keywords

```
PROGRAM   RETURNS   RETURN   END
IF        THEN      ELSEIF   ELSE
DO        WHILE     UNTIL
FOR       TO        STEP     EXIT
AND       OR        NOT      MOD
TRUE      FALSE
INTEGER   DECIMAL   STRING   BOOLEAN
```

### Built-in pattern keywords

```
DECLARE   FINAL
```

### Reserved at seal

Every literal token of every registered statement pattern, every registered function name, and
every registered opaque type name.

### Not BUBAS keywords

`OPAQUE`, `VOID`, `NUMBER` and `ARRAY` never appear in BUBAS source. `OPAQUE` and `VOID` belong
to the Java API; `NUMBER` and `ARRAY` are pattern constraint vocabulary. The placeholder state
words `new`, `declared`, `initialized`, `final` and `mutable` are pattern vocabulary and are not
keywords in BUBAS source either — though `FINAL` separately is one, as a `DECLARE` pattern
keyword.

---

## 16. Open Questions

Deliberately unresolved; each needs a decision before the affected component is built.

1. **DECIMAL to INTEGER conversion.** The prelude converts from `STRING` only. Narrowing a
   `DECIMAL` needs a rounding mode, so it needs a decision rather than a default.
2. **`LENGTH` on strings.** Currently arrays only. A string length belongs either in the
   mandatory prelude or in the optional string package.
3. **Array program parameters.** Parameters are scalar or opaque today. Passing an array in
   would need a parameter type form that does not exist yet.
4. **Diagnostic message catalogue.** Format, message identity and whether errors carry column
   as well as line.
5. **Prompt export format.** Phase 4 exports the function and statement vocabulary for LLM
   consumption; the schema is undecided.

---

## 17. Glossary

| Term | Meaning |
|------|---------|
| **BUBAS** | The orchestration language defined by this document |
| **Language** | A sealed, immutable registry of types, functions, statements and singleton services |
| **Program** | An immutable, fully analysed compilation of one source file |
| **Interpreter** | A cheap, single-use, single-threaded execution of one program |
| **Pattern** | A single-line statement definition with literal words and placeholders |
| **Placeholder** | The variable part of a pattern, e.g. `{mutable > var:total > initialized}` |
| **Kind** | What a placeholder captures: `var`, `expression`, `literal` or `type` |
| **Precondition / postcondition** | What a pattern requires of a variable, and guarantees about it |
| **Opaque type** | A registered Java class that BUBAS can hold and pass but never inspect |
| **Definite assignment** | The analysis ensuring no variable is read before it holds a value |
| **Seal** | The moment registration closes, analysis runs and the reserved-word set is fixed |
| **Service** | A Java object reachable from function and handler implementations, invisible to BUBAS |
