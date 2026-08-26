# BUBAS Language Specification

**Version** 2.0 · **Status** Implemented; see [`README.md`](README.md) for what is built and what is not

BUBAS is an orchestration language for subject matter experts. This document defines the
language, its static semantics, and the Java embedding API. It is the contract between the
BUBAS implementation and the applications that embed it.

---

**Contents**

<!--TOC min-level: 2
max-level: 2
_content_generated_: 722:md5:e95978c844e41424a7364564675e2264
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
- [1. Overview](#1-overview)
- [2. Core Principles](#2-core-principles)
- [3. Architecture](#3-architecture)
- [4. Lexical Structure](#4-lexical-structure)
- [5. Type System](#5-type-system)
- [6. Expressions](#6-expressions)
- [7. Statements](#7-statements)
- [8. Variable State and Static Analysis](#8-variable-state-and-static-analysis)
- [9. The Pattern System](#9-the-pattern-system)
- [10. Java Integration](#10-java-integration)
- [11. Errors](#11-errors)
- [12. The Standard Module](#12-the-standard-module)
- [13. Code Generation](#13-code-generation)
- [14. Worked Example](#14-worked-example)
- [15. Reserved Words](#15-reserved-words)
- [16. Open Questions](#16-open-questions)
- [17. Glossary](#17-glossary)
<!--/TOC-->

---

## 1. Overview

BUBAS is a typed, minimal orchestration language. It is **not** a general-purpose programming
language. It exists so that a subject matter expert can sequence business steps, make decisions,
loop over data and call into Java — and nothing else.

### 1.1. Two layers

| Layer | Written by | Responsible for |
|-------|-----------|-----------------|
| **BUBAS** | Subject matter experts, or an LLM on their behalf | Sequencing, decisions, iteration, calling Java |
| **Java** | Developers | Algorithms, domain objects, infrastructure, persistence, I/O |

### 1.2. What BUBAS is

- A coordination language for non-programmers
- Statically typed, with all type errors reported before execution
- Extensible: a Java developer defines the vocabulary the script author sees
- Predictable enough for an LLM to generate reliably

### 1.3. What BUBAS is not

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

### 3.1. Why three layers

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

### 3.2. Sealing

`seal()` closes registration permanently. From that moment the reserved-word set is fixed, so a
later registration could invalidate an already-compiled program; registering after seal throws.
At seal the implementation verifies that:

- no function name, opaque type name or pattern keyword collides with another, case-insensitively
- no pattern begins with a structural keyword
- placeholder names are unique within each pattern
- type references in patterns resolve
- no two patterns can match the same line (unless overlap analysis is disabled)

### 3.3. Concurrency

A sealed `BubasLanguage` and a `BubasProgram` are immutable and safe to share across threads.
An `Interpreter` is not thread-safe and executes exactly one program, once. Concurrent
orchestration means one `Interpreter` per thread, all sharing one `BubasProgram`.

### 3.4. Components

| Component | Purpose |
|-----------|---------|
| Lexer | Physical lines → tokens and logical lines; handles comments and continuation |
| Pattern matcher | Matches a logical line against the registered patterns |
| Parser | Block structures, expressions, function calls → AST |
| Symbol table | Variable declarations, types and states |
| Analyser | Definite assignment, reachability, use checking |
| Lowering | Types every expression and emits the core tree every back end consumes |
| Interpreter | Executes the AST |
| Function dispatcher | Evaluates arguments, invokes Java implementations |
| Code generator | Emits target-language source from the core tree (Phase 3) |

Two things are built on the language rather than being part of it, and this document defines
neither. They are named here only so that a reader knows where they sit.

| Built on it | What it is | Where it is described |
|-------------|------------|-----------------------|
| BUNIT | Unit testing a BUBAS program, in BUBAS. A vocabulary like any other, which an embedder may replace wholesale — which is exactly why its statements are not specified here | [`BUNIT.md`](BUNIT.md), and a tutorial |
| Vocabulary export | A sealed language described for a generator or a person, from what is derived plus what a `@BubasDescription` says | [10.9](#109-describing-a-language) |

---

## 4. Lexical Structure

### 4.1. Comments

A comment begins with an apostrophe and runs to the end of the **physical** line.

```basic
' A whole-line comment
DECLARE x INTEGER        ' an end-of-line comment
```

An apostrophe inside a string literal does not start a comment.

### 4.2. Logical lines and continuation

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

### 4.3. Keywords

Keywords are case-insensitive; `IF`, `if` and `If` are the same word. The core keyword set is
listed in [§15](#15-reserved-words). Registered command keywords — the literal words of a
statement pattern — are keywords too, and are equally case-insensitive.

The lexer does not classify them, and cannot. The reserved-word set is not fixed until `seal()`,
and it includes every literal token of every registered pattern, every function name and every
opaque type name. So the lexer emits every word-shaped token alike and the analyser classifies
against its registries — the same layering rule as the `NOT` case above. The sole exception is
`AND`, `OR` and `MOD`, which the continuation rule must recognise; those are core and can never
be extended, so knowing them costs the lexer no coupling.

### 4.4. Names

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

### 4.5. Literals

```basic
42        0        -10        1000000            ' INTEGER
3.14      0.5      -2.71828   1000.0             ' DECIMAL
"Hello"   "Line 1\nLine 2"    "He said \"Hi\""   ' STRING
TRUE      FALSE                                  ' BOOLEAN
```

String escapes: `\n`, `\t`, `\r`, `\\`, `\"`.

### 4.6. Punctuation

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

### 4.7. Lossless lexing

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

### 5.1. Types

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

### 5.2. Absence

BUBAS has no `NULL` literal, no null test and no null-producing operation. `null` remains an
ordinary Java value: an opaque array element starts as `null`, and a Java function may return
`null` for an opaque result. BUBAS holds such a value and passes it on without ever looking at
it. A script that must branch on absence uses a function the embedder supplies:

```basic
IF ORDER_WAS_FOUND(purchase) THEN
```

### 5.3. Arrays

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

### 5.4. Assignability

A value of type `S` may be assigned to, or passed as, a target of type `T` when:

- `S` and `T` are the same type, or
- `S` is `INTEGER` and `T` is `DECIMAL`, or
- `S` and `T` are opaque types and `S`'s Java class is assignable to `T`'s, or
- `S` and `T` are arrays with the **same** element type

Opaque assignability follows Java, interfaces included. Nothing has to compute or cache a lattice:
the registered classes carry it already, so the check is `Class.isAssignableFrom`.

Nothing else converts implicitly. There is no narrowing and no cast.

**Arrays are invariant.** An array of `RushOrder` is not an array of `Order`, even though a
`RushOrder` is an `Order`. Java arrays are covariant here and pay for it with a runtime
`ArrayStoreException`; BUBAS closes the hole statically instead, and can afford to because array
assignability is consulted in exactly one place — matching an array argument against a function
parameter. Arrays are never assigned and never returned.

The reason it matters is that an array crosses into Java as the interpreter's **backing store**,
not a copy, which is what makes an in-place reorder visible to the script
([§10.2](#102-signatures-are-derived-from-java)). Under covariance a handler declaring `Order[]`
could store a plain `Order` into an array the script declared as `RushOrder`, and the only thing
standing in the way would be a Java exception raised inside embedder code, with nothing naming the
line that passed the array.

```basic
DECLARE rushes[10] RushOrder
SORT_ORDERS(rushes)
' error: SORT_ORDERS expects Order[], found RushOrder[]
```

The cost is that a function which only *reads* is refused too, where reading would have been safe.
Such a function can declare `ANY_ARRAY` instead, at the price of casting `BubasArray.raw()`.

> **Rationale (not normative).** Array assignment is absent by choice, not by omission. Keeping
> arrays out of expressions keeps the language small, and it steers orchestration scripts away from
> moving data around — which is Java's job, and the whole premise of the two layers.
>
> The capability is not withheld, only the spelling. An integration that genuinely needs to put a
> whole array into a variable defines a command for it, and the handler receives both arrays and
> does as it likes:
>
> ```basic
> COPY orders INTO archive
> ```
>
> The restriction is that this may not be written as an assignment. That is deliberate: a script
> author reading `=` should be able to assume one value moved, not that an entire collection was
> aliased or copied — and which of those happened would be invisible at the assignment.

### 5.5. Type vocabulary outside BUBAS source

`NUMBER`, `ARRAY` and `VOID` never appear in BUBAS source. `NUMBER` and `ARRAY` are pattern
constraint vocabulary ([§9.4](#94-constraints)); `VOID` is a Java-side function return type.

---

## 6. Expressions

### 6.1. Operators and precedence

Highest to lowest:

1. `(` `)`
2. unary `NOT`, `-`, `+`
3. `*` `/` `MOD`
4. `+` `-`
5. `=` `<>` `<` `>` `<=` `>=`
6. `AND`
7. `OR`

All binary operators are left-associative.

### 6.2. INTEGER arithmetic

- `/` truncates toward zero: `7 / 2` is `3`, `-7 / 2` is `-3`
- `MOD` takes the sign of the dividend: `-7 MOD 2` is `-1`
- Division or `MOD` by zero is a runtime error
- Overflow is a runtime error, never a wraparound

### 6.3. DECIMAL arithmetic

`+`, `-` and `*` are exact. `/` uses the interpreter's `MathContext`, which defaults to
`MathContext.DECIMAL128` (34 digits, `HALF_EVEN`) and may be changed at runtime by a Java
function. Consequently:

- the same source may produce different results across runs, by design
- there is **no compile-time constant folding**, of division or of anything else
- generated Java reads the `MathContext` from the runtime rather than baking it in

### 6.4. Comparison

| Operand types | Legal operators |
|---------------|-----------------|
| `INTEGER`, `DECIMAL`, mixed | all six |
| `STRING` | all six, lexicographic by code point |
| `BOOLEAN` | `=` `<>` only |
| opaque | none |

`DECIMAL` equality compares numeric value, not representation: `2.0 = 2.00` is `TRUE`. Mixed
`INTEGER`/`DECIMAL` comparison widens the integer first.

### 6.5. String concatenation

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

### 6.6. Function calls in expressions

Parentheses are mandatory in an expression. A function used in an expression must not return
`VOID`.

```basic
purchase = LOAD_ORDER(orderId)
IF VALIDATE_ORDER(order) AND IS_URGENT(order) THEN
```

---

## 7. Statements

### 7.1. Program structure

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

### 7.2. Declarations

```basic
DECLARE count INTEGER
DECLARE total DECIMAL = 0.0
DECLARE rate DECIMAL FINAL = 0.07
DECLARE numbers[5] INTEGER
DECLARE items[COUNT_ORDERS()] Order
```

A `FINAL` variable requires an initializer and can never be reassigned. Finality is not a state
a variable enters later: what is final is final from its declaration.

**A declaration may appear only at the top level of a program** — never inside an `IF` arm, a `DO`
or a `FOR`.

```basic
PROGRAM P
    DECLARE total DECIMAL          ' here
    IF over THEN
        DECLARE note STRING        ' error: only at the top level
    END IF
END.
```

The rule is not about `DECLARE`, which is only a pattern like any other. It covers **any statement
whose pattern creates a variable** — anything carrying a `new` precondition or a `final`
postcondition — so a custom `FETCH {type:T} INTO {new > identifier/T:out > initialized}` is bound
by it too.

> **Rationale (not normative).** BUBAS has no local variables. A declaration inside a block would
> therefore look scoped while being global: the name would outlive the block, keep its value after
> it, and collide with a later declaration elsewhere — none of which the indentation suggests. The
> appearance of locality without the substance is worse than not offering it.
>
> It also nudges in the direction the two layers already point. A script needing variables declared
> deep inside nested blocks is doing work that belongs in Java, and having to hoist every
> declaration to the top makes that visible while the script is being written rather than after.
>
> The analysis gets simpler as a side effect — declaredness stops needing flow analysis entirely —
> but that is a consequence, not the reason.

### 7.3. Assignment

Assignment has no keyword. BUBAS is not BASIC; `LET` faded out of the languages that had it, and
no language written since asks for it on the most frequent line in every script.

```basic
count = 0
numbers[i] = numbers[i] + 1
```

The target must be declared and not final. The value must be assignable to the target's declared
type. Assignment is the one built-in whose pattern carries no keyword at all, which is why a
pattern is not required to begin with one — see [§9.1](#91-matching).

### 7.4. Conditionals

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

### 7.5. Loops

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

### 7.6. Return

```basic
IF NOT VALIDATE_ORDER(order) THEN
    LOG_EVENT("ERROR", "invalid order")
    RETURN FALSE
END IF
RETURN TRUE
```

`RETURN` may appear anywhere, including inside loops, and terminates the program.

### 7.7. Statement-form calls

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

### 8.1. Two axes

Variable state is a pair, not a single enum.

| Axis | Values |
|------|--------|
| Assignment | `UNDECLARED` → `DECLARED` → `INITIALIZED` |
| Mutability | `MUTABLE` or `FINAL`, fixed at declaration |

`FINAL` implies `INITIALIZED` at the point of declaration. A variable never becomes final later,
and never becomes uninitialized.

**Only initialization is flow-sensitive.** Because a declaration may appear only at the top level
([§7.2](#72-declarations)), it always runs, on every path, before anything that could use it.
Declaredness therefore needs no flow analysis at all: a name is known to every line after its
declaration and to none before, and a write needs no declaredness check that a read does not
already imply. What varies by path is only whether the variable holds a value.

```basic
DECLARE x INTEGER
IF c THEN
    x = 1
END IF
x = x + 1        ' error: x is not definitely initialized
```

### 8.2. Definite assignment

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

### 8.3. Rejected at compile time

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

### 9.1. Matching

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

### 9.2. Placeholder syntax

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

**A placeholder may not be named after a type** — neither a built-in scalar nor a registered
opaque type. This is checked at `seal()`, because opaque types are not known when a pattern is
parsed.

The rule exists to make constraint resolution total. A constraint `/X` means "the same type as
placeholder `X`" when the pattern has one by that name, and "the type named `X`" otherwise. If a
pattern could contain `{type:Order}` while `Order` were also a registered opaque type, `/Order`
would have two readings and nothing could choose between them. Forbidding the collision means the
two cases can never both apply.

> **Rationale (not normative).** The rule is not the only defensible answer. A placeholder name is
> local to one pattern while an opaque type name is global, and locality is the stronger claim — so
> letting a placeholder shadow a type would be perfectly coherent, with the pattern simply meaning
> its own `X`.
>
> Shadowing only starts to pay, though, when a command set has grown large enough that avoiding
> collisions becomes a chore. That is not the size BUBAS is built for. A command set is expected to
> number ten or a few tens, small enough that its author can hold the whole vocabulary in view as
> one thing. At that size choosing a different placeholder name costs nothing, and no reader ever
> has to work out which `X` a constraint meant. A vocabulary that has outgrown being comprehensible
> as a unit has a problem that a shadowing rule would not fix.

### 9.3. Kinds

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

### 9.4. Constraints

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

### 9.5. Preconditions and postconditions

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

### 9.6. The standard statements

A language without declaration and assignment is unusable, so the standard module supplies them.
They are **ordinary patterns with ordinary implementations** — nothing in the language privileges
them, nothing treats them specially at run time, and an embedder who wants different ones simply
does not install these. Shipping them only spares every integration from writing declaration and
assignment for itself.

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

That last point is why `DECLARE x INTEGER` has an **empty implementation**. The placeholder carries
a type constraint, so name, type and finality are fixed before the handler runs and the runtime has
already made the slot; with a `declared` postcondition there is no value to supply and nothing left
to do. The other four do one thing each — evaluate the initializer and write it, or allocate the
array — which is the whole of what makes them "built in".

### 9.7. Custom statements

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

### 10.1. One class, one thing

Every function and every command is implemented by its own class. The runtime instantiates it
once per sealed language, through a public no-arg constructor or a public static `provider()`
method — deliberately the same contract `ServiceLoader` uses, so one class works through either
registration route.

The implementation is **the single public instance method the class declares**. Static methods are
excluded, which is what lets a `provider()` factory coexist with it: the class is instantiated, so
a static method could never have been the one being called. Its name is irrelevant;
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

#### 10.1.1. Naming a command

A function is named where it is registered. A command is not — its pattern is its syntax — so it
gets a name derived from that pattern: the **skeleton**, every literal verbatim and every
placeholder written `_`.

```
VALIDATE {initialized > var/Order:item} AGAINST {expression:rules}   →   VALIDATE _ AGAINST _
LOG_EVENT {literal/STRING:level}, {expression:message}               →   LOG_EVENT _, _
DECLARE {new > identifier/ARRAY/T:name}[{expression:size}] {type:T}  →   DECLARE _[_] _
{mutable:declared > var:name > initialized} = {expression:value}     →   _ = _
```

The skeleton is derived, so it cannot drift from the pattern, and it is injective in practice: two
patterns sharing a skeleton differ only in placeholder kind and would have to survive overlap
analysis first. Runs of whitespace collapse, so an embedder's stray double space cannot change a
name.

`@BubasCommandName("LoanValidation")` on the implementation class replaces it. This is for a team
that prefers a domain name to a skeleton, and it is the escape route when two patterns really do
share one. The name **replaces** the skeleton rather than adding an alias — once a command is named,
its skeleton no longer refers to it, because two ways to name one thing is how two halves of a test
suite end up written in different dialects.

A declared name is non-blank and contains no whitespace, which is also what keeps it from ever
looking like a skeleton. Two declared names in one language may not differ only in case, and
`seal()` says so naming both patterns. Undeclared names are not checked against each other: an
application that never mocks anything should not be made to invent names for commands nobody will
refer to.

### 10.2. Signatures are derived from Java

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

#### 10.2.1. The `ANY` parameter

A function parameter declared as `Value` accepts any BUBAS value:

```java
public String call(Context ctx, Value value)      // SHOW(value ANY) -> STRING
public String call(Context ctx, Value... parts)   // CONCAT(parts ANY...) -> STRING
```

`ANY` accepts every type but `VOID`, and **nothing accepts `ANY`**. That asymmetry is the whole
design: a wildcard may be a parameter and may never be a return type, because a returned value
enters the script, where its type has to be known for anything downstream to be checked. A function
declaring `Value` or `BubasArray` as its return type is rejected at `seal()`.

The value arrives boxed, carrying its own [`type()`](#108-values-and-arguments) alongside the data,
so the handler is told what it was given rather than having to guess. The conversions are not
lenient — `asString()` on an INTEGER is a diagnostic, not a coercion — so a handler either switches
on `type()` or takes the raw form with `as(Object.class)`. An array reaching an `ANY` parameter
arrives as its raw Java array; a handler that wants element-agnostic array access declares
`BubasArray` and gets `ANY_ARRAY` instead.

`ANY` cannot be written in BUBAS source, held by a variable, or named in a pattern constraint. It
exists only in a signature, derived from a Java parameter, which is what keeps the script's static
typing whole. It is the scalar sibling of `ANY_ARRAY`, which has always existed for the same reason:
`LENGTH` is element-agnostic, and some operations are value-agnostic.

#### 10.2.2. Variadic functions

A variadic Java method is a variadic BUBAS function. The signature records the *element* type, so

```java
public String call(Context ctx, String label, long... numbers)
```

reads as `LABELLED(label STRING, numbers INTEGER...) -> STRING`. A call must supply the fixed
parameters and may then supply any number of further arguments, each checked against the element
type. Too few reports how many are needed: *"takes at least 1 argument(s) but was given 0"*.

**Only the spread form is accepted.** Java allows a variadic method to be called either way —
`join(array)` or `join("a", "b")` — and BUBAS allows only the second. Arrays here are invariant,
first-class values, and admitting both forms would revive exactly the overload ambiguity the single
form avoids; a second way to do one thing invites inconsistency. An embedder who wants to receive
an array declares an array parameter, which is a different and equally available signature.

**A command may not be variadic.** A command's parameters match the placeholders of its pattern,
which are fixed in number, so a variadic handler could never be filled. It is rejected at `seal()`
rather than silently derived as an array parameter.

### 10.3. Building a language

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

#### 10.3.1. Defining twice

A name may be defined once. Defining it again — a function, an opaque type, or a statement with the
same pattern — is an error naming both, because it used to be neither deliberate nor reported: the
second definition simply won, and a language whose behaviour depended on the order two bundles were
installed in said nothing about it.

Replacing is available and has to be said:

```java
BubasLanguage.builder()
    .install(Standard::register)
    .override().defineFunction("LENGTH", CountingCharacters.class)
    .seal();
```

`override()` covers exactly one definition and is then spent, so it cannot drift down a chain and
authorise something further along. It fails when the name is *absent*, because an override of
nothing is a rename nobody finished. A map of definitions is replaced with `overrideAll()` instead;
neither form substitutes for the other, and neither may be left pending when `install()` or `seal()`
is reached — a bundle decides its own definitions, and an override has to name the one thing it
replaces.

#### 10.3.2. Services on the language and on the run

A service registered on the builder is shared by every interpreter that language produces, which is
what makes a singleton a singleton instead of something each run has to be handed again. A service
registered on the interpreter ([§10.4](#104-compiling-and-running)) belongs to that run alone. When
both register the same type and qualifier, the run wins.

Two consequences follow from a language being sealed once and shared. A language-level service is
one object serving every run, including runs on different threads at the same time, so it must be
thread-safe; and anything that varies per run — a transaction, a request, a user — belongs on the
interpreter, which is single-use and single-threaded. Neither is enforced: the split is a contract
between the embedder and itself.

Services are not part of [`Registrar`](#103-building-a-language). A bundle defines vocabulary; a
service is a live object, and a library that supplied one would be choosing the collaborator for
every embedder that installs it. The embedder registers services, on the builder or on the run.

Each `define` call has a plural form taking a map, for a vocabulary assembled elsewhere:

```java
.defineOpaqueTypes(Map.of("Order", Order.class, "Customer", Customer.class))
.defineFunctions(orderFunctions)
.defineStatements(orderStatements)
```

A map preserves nothing the language depends on: two patterns that match one line are an error
rather than something resolved by registration order ([§9.1](#91-matching)), so a plain `HashMap`
is as correct as a `LinkedHashMap`. Order affects only the sequence diagnostics are reported in.

#### 10.3.3. Bundles

A vocabulary that several embedders share is packaged as a method taking a `Registrar` and applied
with `install`:

```java
BubasLanguage lang = BubasLanguage.builder()
    .install(Standard::register)
    .install(OrderVocabulary::register)
    .defineOpaqueType("Order", Order.class)
    .seal();
```

```java
public final class OrderVocabulary {
    public static void register(Registrar registrar) {
        registrar.defineFunction("LOAD_ORDER", LoadOrder.class)
                 .defineStatements(STATEMENTS);
    }
}
```

`Registrar` is the registration half of the builder: the six `define` calls, plus `install` itself
so that a bundle may delegate to further bundles. It omits `seal()` and `skipOverlapAnalysis()`
deliberately — those are the embedder's decisions, and a library that made them on the embedder's
behalf would be making them for every other library in the same chain. The narrowing is static: a
determined caller can cast back to the builder. It exists to keep an honest bundle inside its
remit, not to contain a hostile one.

`Registrar` is declared in `bubas-api` rather than beside the builder, because the libraries it
exists for depend on the API alone. A bundle-authoring interface that dragged in the analyser would
defeat its own purpose.

Every method of `Registrar` returns `Registrar`, so definitions chain inside a bundle. The builder
overrides each one with a covariant `Builder` return, which is what lets an embedder's chain run
through `install` and still end in `seal()`.

Installing a bundle is not the same as discovering an extension ([§10.5](#105-extensions-and-discovery)).
A bundle is a method the embedder names explicitly; discovery finds candidates on the classpath.
Both end at the same `define` calls, and both are opt-in, but only one of them is a name the
embedder wrote down. The `extensions()` selector in the example above is planned and
not implemented; [§10.5](#105-extensions-and-discovery) records why it may stay that way.

### 10.4. Compiling and running

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

#### 10.4.1. Interception

An interpreter may be given a `BubasCallInterceptor`, which answers for a function or a command in
place of its implementation:

```java
Value result = Interpreter.of(prog).intercept(recorder).run();
```

```java
public interface BubasCallInterceptor {
    boolean interceptsFunction(String name);
    Value   onFunction(String name, List<Value> arguments);
    boolean interceptsCommand(String pattern);
    void    onCommand(String pattern, StatementContext context, Map<String, Object> arguments);
}
```

This exists for a test framework and for nothing else. It bypasses the implementations a language
was sealed with, which is exactly what a mock needs and exactly what production code must not do.

Four methods rather than a nullable result, because "not intercepted" and "intercepted, returns
nothing" are different answers and `VOID` functions are real. A function's arguments arrive
evaluated, boxed with their static types, and **spread rather than packed** for a variadic call: a
mock matches on what the script wrote, not on how the Java method receives it. A command receives
**the handler's own argument objects**, keyed by placeholder name, so an interceptor can write what
the real command would have written — a command whose pattern declares a variable otherwise leaves
the script reading an unassigned slot, with definite assignment already satisfied at compile time
and nothing to warn.

Interception is installed on an interpreter and never on a language, so a sealed language knows
nothing about testing and the same checked program runs either way. Not installing one runs the real
implementations, which is what makes an integration mode free rather than a second code path.

BUNIT is the framework built on it: see [`BUNIT.md`](BUNIT.md), which is not normative. Its statements are a vocabulary, not part of the language, and
another test vocabulary may replace them without any of this section changing.

### 10.5. Extensions and discovery

**Planned, not implemented.** Nothing in this section exists yet: not `BubasExtension`,
`BubasFunction`, `BubasCommand`, the abstract bases, the `@BubasFunction` and `@BubasCommand`
annotations, nor the `extensions()` selector in [§10.3](#103-building-a-language). Registration
today is explicit — one `defineFunction`, `defineStatement` or `install` call at a time. The
`provider()` static factory that [§10.1](#101-one-class-one-thing) accepts is real, and it is
deliberately the contract `ServiceLoader` uses, so a class written for discovery already works
through explicit registration. There is no annotation for services, discoverable or otherwise;
services are registered imperatively and only ever per run.

> **Rationale (not normative).** Discovery is contemplated rather than committed, and the reason is
> the philosophy the rest of this document is built on: a BUBAS program can name nothing it was not
> given. The builder API states the vocabulary at the point of registration, so an embedder reads
> the entire capability surface of a language off one chain in their own source. Discovery inverts
> that. What a language contains becomes a function of what is on the classpath, which is decided by
> the build — often transitively, by dependencies the embedder never chose and does not read.
>
> A filter narrows the damage without repairing the property. A predicate over discovered
> candidates is a denial-list: it asks the embedder to anticipate what might appear rather than to
> state what should. An embedder who writes no filter has a vocabulary they cannot enumerate from
> their own code, and every reserved word in it is a word a script may no longer use as a variable.
>
> Nothing here is a soundness argument. Registration stays opt-in, so discovery could never widen a
> language silently, and [§10.3](#103-building-a-language) keeps that guarantee. The cost is
> legibility and review — which is exactly the thing this design is least willing to spend, because
> "the script can only do what you handed it" is worth much less when what you handed it takes a
> classpath dump to establish.
>
> Bundles have also removed most of the ergonomic pressure that motivated discovery. A library
> ships one `register(Registrar)` method, the embedder writes one `install(Acme::register)` line,
> and the vocabulary is both packaged and named. That is nearly all of the convenience at none of
> the cost, which is why discovery may end up not being worth building at all.

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

#### 10.5.1. Registration

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

### 10.6. Types

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

### 10.7. Contexts

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

#### 10.7.1. Ambient configuration

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

### 10.8. Values and arguments

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

#### 10.8.1. An indexed reference

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

#### 10.8.2. A whole array

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

### 10.9. Describing a language

A signature says what a function *takes*. Nothing derived says what it *means*, and meaning is what
a reader who is not looking at the Java needs: a subject matter expert choosing an operation, a code
generator being told what a vocabulary is for.

```java
@BubasDescription("Finds an order by the identifier the customer was given. Fails if none.")
public final class LoadOrder {
    public Order call(Context ctx, long orderId) { … }
}
```

**A description must not state anything that can be derived.** Names, parameter names and types,
return types, variadicity, patterns, placeholder kinds, pre- and postconditions are all read off the
code and appear beside the prose in an export. A description repeating them adds nothing and will
one day contradict them — and the prose is always the half that is wrong. What cannot be derived is
what the description is for: what the operation means, when to reach for it, what it fails on.

#### 10.9.1. Where an opaque type's description lives

An opaque type is usually a domain class — `Order`, `Claim` — that a REST layer, a rules engine and
BUBAS all hold. Annotating it would make the domain model depend on one of its consumers, and
describing it at each registration would put the same prose in as many places as there are languages
exposing it. An empty interface is neither:

```java
@BubasDescribes(Order.class)
@BubasDescription("An order a customer placed, as the order service knows it.")
public interface OrderDoc {
}
```

```java
.defineOpaqueTypeVia("Order", OrderDoc.class)
```

It is a separate call rather than something `defineOpaqueType` notices, because registering a class
other than the one named in the call reads well until it surprises someone.

#### 10.9.2. Saying a description was reviewed

Prose goes stale silently: the class gains a method, changes a signature, and sentences that were
true last year quietly are not. `@BubasReviewed` records the checksum of the described class's
public surface, and `seal()` refuses a language whose descriptions were reviewed against a different
shape.

Three states, and the difference between the last two matters:

| | |
|---|---|
| annotation absent | nothing is checked — reviewing is opt-in per class |
| empty value | the first time: sealing reports the checksum to write, and asks nobody to review anything, because there is nothing yet to compare against |
| a value | checked; a mismatch prints the current surface and names what to re-read |

The checksum is reported, never written for you. A build that edits its own sources to make itself
pass has stopped being a check.

What it catches is a change of *shape*. A function whose behaviour changed and whose signature did
not moves no checksum, and nothing here reaches that: a green checksum means the description was
reviewed against this shape, not that it is true.

#### 10.9.3. The export

```java
var export = VocabularyExport.of(language);
Files.writeString(prompt, export.asMarkdown());
Files.writeString(schema, export.asJson());
```

Derived and written, together and not overlapping: shape from the language, meaning from the
annotations.

**No Java appears in it** — no class names, no packages, no implementation detail. Whoever reads
this is going to write BUBAS, and the Java behind a function matters to them the way a bicycle
matters to a fish. It also means an export can be handed to someone without handing them an
inventory of the host application's internals.

**Descriptions are required by the export and nowhere else.** A language without them seals,
compiles and runs perfectly; it simply cannot be exported, because an export with holes in it reads
like documentation, which is worse than having none. Putting the requirement here rather than on the
builder means nobody who does not export pays for it, and nobody who does can forget — you cannot
get an export without satisfying it. Everything undescribed is named at once, since an author
filling holes one rebuild at a time gives up before the third.

`bubas-export` is a module of its own that nothing depends on. An export is a build-time artefact,
and keeping the machinery off every other module's classpath makes shipping it a decision rather
than an accident.

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

### 11.1. Definition errors

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

## 12. The Standard Module

Everything a language needs but nobody should have to write: the statements of
[§9.6](#96-the-standard-statements) and the few functions the specification itself depends on.

```
DECLARE x T                  the four declaration forms
x = e                        assignment, indexed or not

TO_INTEGER(s STRING) -> INTEGER
TO_DECIMAL(s STRING) -> DECIMAL
LENGTH(a ANY_ARRAY)  -> INTEGER
```

The whole module installs as a bundle:

```java
BubasLanguage.builder().install(Standard::register).seal();
```

Nothing here is privileged. These are ordinary patterns with ordinary implementations, and an
embedder that wants a different declaration or assignment syntax simply does not install them.

There is no `TO_STRING`: `"" + x` already converts, and a second way to do one thing invites
inconsistency. There is no null test, because there is no null in the language.

None of it is privileged. These are ordinary patterns and ordinary functions, installed like any
other vocabulary and excluded by not installing them — a language that wants a different
declaration syntax, or none, is free to have one. The standard module depends only on the API, so a
vocabulary library never has to depend on the interpreter.

Larger vocabularies — string manipulation, regular expressions, date handling, mathematics — ship
as further packages, installed the same way (Phase 2).

---

## 13. Code Generation

A `BubasProgram` can be compiled to another language. Java is the first target; the design does not
assume it is the only one.

### 13.1. What the back ends share

Both the interpreter and every code generator consume the same **core tree**, produced once by
lowering the checked program. The core tree exists for exactly this reason. In the syntax tree a
`+` could mean integer addition, decimal addition or string concatenation, and each back end would
have to work that out for itself — as many chances to decide differently as there are back ends,
in precisely the subtle ways a debugged script would only discover in production. Lowering makes
the choice once, so what a back end receives is a named operation rather than a specification to
re-read:

```
Binary("+", INTEGER, DECIMAL)   →   AddDecimal(WidenToDecimal(l), r)
Binary("+", STRING, INTEGER)    →   Concat(l, TextOf(r))
Binary("+", INTEGER, INTEGER)   →   AddIntegerChecked(l, r)
Binary("=", DECIMAL, DECIMAL)   →   CompareDecimalByValue(l, r)
```

Widening, text conversion, overflow checks, bounds checks and the `MathContext` a decimal division
reads are all explicit nodes. Nothing is inferred twice.

It is a lowered **tree**, not a flat instruction list: keeping `IF`, `DO` and `FOR` structure means
a Java generator emits natural Java rather than reconstructing control flow from jumps, and source
positions survive so generated code maps back to the script.

### 13.2. Commands

Every statement pattern is a command with an implementation, including the standard ones. A code
generator may therefore always fall back to emitting a call to the very implementation the
interpreter calls — which is the guarantee that any vocabulary compiles at all.

A command that wants better output supplies a **target-independent semantic description** of what
it does, and the generator uses that instead. Assignment described that way becomes an assignment
in the target language rather than a call into the runtime. The form of that description is a
Phase 3 question and deliberately not settled here; what is settled is that it is optional, and
that its absence costs output quality rather than correctness.

### 13.3. Limits

Functions and commands are Java classes. A target that is not Java can share the core semantics but
cannot call the vocabulary: every function would need an implementation in that language. The
equivalence guarantee covers what BUBAS pins, and stops where the embedder's code begins.

### 13.4. Conformance

The generated code and the interpreter are separate implementations of one operation set, so **a
conformance suite is mandatory**: every test program must run through both back ends and produce
identical results and identical errors. Divergence is the characteristic bug of this design, and
only the suite will catch it.

Stepping is deliberately not part of it. Compiled output targets production and carries no BUBAS
debugging; a script is debugged interpreted, and generated Java is debugged as Java — which is why
readable output that maps back to the source is a requirement on the generator rather than a
nicety.

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

<!--INCLUDE
from: "bubas-analyser/src/test/resources/examples/approve-order.bu"
prefix: "```basic"
postfix: "```"
_content_generated_: 575:md5:fc969a8fa81e2450798c25333daf293c
# ⚠️ MANAGED CONTENT: Edits will be lost.
# danger zone: Delete _content_generated_ to override.
-->
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
<!--/INCLUDE-->

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

### 15.1. Core keywords

```
PROGRAM   RETURNS   RETURN   END
IF        THEN      ELSEIF   ELSE
DO        WHILE     UNTIL
FOR       TO        STEP     EXIT
AND       OR        NOT      MOD
TRUE      FALSE
INTEGER   DECIMAL   STRING   BOOLEAN
```

### 15.2. Built-in pattern keywords

```
DECLARE   FINAL
```

### 15.3. Reserved at seal

Every literal token of every registered statement pattern, every registered function name, and
every registered opaque type name.

### 15.4. Not BUBAS keywords

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
6. **Whether extension discovery is built at all.** [§10.5](#105-extensions-and-discovery) is
   specified but unimplemented, and the rationale there argues that bundles ([§10.3](#103-building-a-language))
   deliver its convenience without moving the vocabulary out of the embedder's own source. The
   decision is to build it, drop it, or keep the annotations and abandon only classpath scanning.

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
