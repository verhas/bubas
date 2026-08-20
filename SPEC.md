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
        │  newInterpreter()
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
LET total = subtotal +
            tax +
            shipping

LET x = COMPUTE(alpha,
                beta)

LET first = names[index +
                  offset]

VALIDATE order _
    AGAINST rules
```

Rules:

- an underscore inside a string literal is an ordinary character, not a continuation
- a trailing comment on an intermediate physical line is stripped before joining
- a string literal never spans a line break
- when a bracket is never closed, the diagnostic names the line where the bracket **opened**

### 4.3 Keywords

Keywords are case-insensitive; `IF`, `if` and `If` are the same word. The core keyword set is
listed in [§15](#15-reserved-words). Registered command keywords — the literal words of a
statement pattern — are keywords too, and are equally case-insensitive.

### 4.4 Names

Variable names, function names and opaque type names start with a letter or underscore and
continue with letters, digits and underscores.

Names are **unique case-insensitively** and **written exactly as declared or registered**. A
declaration reserves the name in every casing; a later reference must match it character for
character. This rules out lookalike pairs and capitalisation typos in one rule.

```basic
DECLARE userId INTEGER
DECLARE UserID STRING    ' error: collides with userId
LET UserId = 5           ' error: declared as 'userId'
LET userId = 5           ' correct
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
DECLARE order Order
DECLARE buyer Customer
```

### 5.2 Absence

BUBAS has no `NULL` literal, no null test and no null-producing operation. `null` remains an
ordinary Java value: an opaque array element starts as `null`, and a Java function may return
`null` for an opaque result. BUBAS holds such a value and passes it on without ever looking at
it. A script that must branch on absence uses a function the embedder supplies:

```basic
IF ORDER_WAS_FOUND(order) THEN
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
- An array has no expression type: `LET x = numbers` is an error. An array may appear as a
  bare argument in a function call, and nowhere else

```basic
SORT_ITEMS(items)        ' legal: bare array name as an argument
LET copy = items         ' error: an array is not an expression
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
LET order = LOAD_ORDER(orderId)
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

```basic
LET count = 0
LET numbers[i] = numbers[i] + 1
```

The target must be declared and not final. The value must be assignable to the target's
declared type.

### 7.4 Conditionals

```basic
IF score >= 90 THEN
    LET grade = "A"
ELSEIF score >= 80 THEN
    LET grade = "B"
ELSE
    LET grade = "C"
END IF
```

`ELSEIF` is one word; `ELIF`, `ELSIF` and a two-word `ELSE IF` are not accepted. Because
statements are line-based, `ELSE IF x THEN` is two statements on one line and is rejected
without needing a special rule.

### 7.5 Loops

```basic
DO WHILE count < 10          DO
    LET count = count + 1        LET count = count + 1
END DO                       END DO UNTIL count >= 10

DO UNTIL done                DO
    LET done = STEP_DONE()       LET done = STEP_DONE()
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
        LET hit = i
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
intersection. The check is conservative and can reject a pair that would never actually collide;
`skipOverlapAnalysis(true)` disables it, both for startup cost in production and for grammars
whose author knows better.

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
{new > var:x}
{var:total > initialized}
{mutable:initialized > var:total > initialized}
{initialized > var/Order:o}
{expression/T:init}
{literal/INTEGER:times}
{type:T}
```

An unnamed placeholder takes its **kind** as its name: `{expression}` is named `expression`,
`{new > var}` is named `var`. Placeholder names must be unique within a pattern, which is why
at most one placeholder of each kind may be left unnamed. State keywords may not be used as
placeholder names, which is what makes `{var:total}` and `{var:initialized}` distinguishable.

### 9.3 Kinds

| Kind | Captures |
|------|----------|
| `var` | A variable name, with state requirements and guarantees |
| `expression` | A full expression, evaluated lazily by the handler |
| `literal` | A literal, required to be a compile-time constant |
| `type` | A type designator |

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

| Written | Means |
|---------|-------|
| `/T` where `T` is a `{type:T}` hole | the type actually written at that position |
| `/x` where `x` is a `{var:x}` hole | that variable's declared type |
| `/e` where `e` is an `{expression:e}` hole | that expression's static type |
| `/a[]` where `a` is array-typed | that array's element type |

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

Rules: `new` is a prefix only; a `final` postfix implies a `new` prefix; `final` cannot be
combined with a `declared` or `initialized` prefix; the postcondition of a custom statement is
**verified when its handler returns**, and a handler that fails to deliver what its pattern
promises raises an error at its own statement rather than corrupting the analyser's model.

### 9.6 Built-in patterns

The built-ins are ordinary patterns, and expand to core AST nodes rather than handler calls —
which is what allows them to be compiled to standalone Java.

```
DECLARE {new > var:name > declared} {type:T}
DECLARE {new > var:name > initialized} {type:T} = {expression/T:init}
DECLARE {new > var:name > final} {type:T} FINAL = {expression/T:init}
DECLARE {new > var:name > initialized}[{expression/INTEGER:size}] {type:T}

LET {mutable:declared > var:name > initialized} = {expression/name:value}
LET {initialized > var/ARRAY:a}[{expression/INTEGER:index}] = {expression/a[]:value}
```

Note how `{expression/name:value}` and `{expression/a[]:value}` express, for the first time, that
an assignment's right-hand side must match the target's declared type or element type.

### 9.7 Custom statements

```java
builder.defineStatement("VALIDATE {initialized > var/Order:item} AGAINST {expression:rules}")
       .as(ctx -> {
           Order order = ctx.variable("item").get().as(Order.class);
           Value  rs   = ctx.expression("rules").evaluate();
           if (!validate(order, rs.asString())) {
               ctx.error("validation failed for " + ctx.variable("item").name());
           }
       });
```

```basic
VALIDATE order AGAINST rules
```

Handler arguments are **lazy**: an expression is evaluated when, and as often as, the handler
asks. That is what lets a custom statement express control flow rather than only side effects:

```java
builder.defineStatement("EXECUTE {expression} TIMES {expression/INTEGER:n}")
       .as(ctx -> {
           long times = ctx.expression("n").evaluate().asLong();
           for (long i = 0; i < times; i++) {
               ctx.expression("expression").evaluate();
           }
       });
```

---

## 10. Java Integration

### 10.1 Building a language

```java
BubasLanguage lang = BubasLanguage.builder()
    .defineOpaqueType("Order", Order.class)
    .defineOpaqueType("Customer", Customer.class)

    .registerService(Clock.class, systemClock)
    .registerService(DataSource.class, "read",  readOnlyDs)
    .registerService(DataSource.class, "write", primaryDs)

    .defineFunction("LOAD_ORDER")
        .parameter("orderId", BubasType.INTEGER)
        .returns(BubasType.opaque("Order"))
        .as(ctx -> orderService.load(ctx.argument("orderId").asLong()))

    .defineFunction("LOG_EVENT")
        .parameter("level",   BubasType.STRING)
        .parameter("message", BubasType.STRING)
        .returns(BubasType.VOID)
        .as(ctx -> {
            ctx.log(ctx.argument("level").asString(),
                    ctx.argument("message").asString());
            return null;
        })

    .defineStatement("VALIDATE {initialized > var/Order:item} AGAINST {expression:rules}")
        .as(ctx -> { ... })

    .seal();
```

### 10.2 Compiling and running

```java
BubasProgram prog = lang.compile(source);

for (long id : orderIds) {
    Value result = prog.newInterpreter()
        .argument("orderId", id)
        .argument("region", "EU")
        .registerService(Transaction.class, tx)
        .mathContext(MathContext.DECIMAL128)
        .run();

    if (result.asBoolean()) { ... }
}
```

`run()` may be called once per `Interpreter`; a second call throws.

### 10.3 Types

```java
BubasType.INTEGER
BubasType.DECIMAL
BubasType.STRING
BubasType.BOOLEAN
BubasType.VOID                    // function return type only
BubasType.opaque("Order")         // a registered opaque type
BubasType.arrayOf(BubasType.INTEGER)
BubasType.ANY_ARRAY               // parameter position only
```

`ANY_ARRAY` accepts an array of any element type. It is legal only as a parameter type — never
for a variable, a return type or a constraint.

### 10.4 Functions

```java
FunctionSignature {
    String name;                  // reserved case-insensitively, written as registered
    BubasType returnType;         // VOID for a procedure
    List<Parameter> parameters;   // named; Context is not among them
}
```

One signature per name; there is no overloading. Arguments are evaluated **eagerly**, in order,
before the implementation runs, and each must be assignable to its parameter type. A signature
is used by the parser to recognise calls, by the analyser to check them, by the code generator
to emit type-safe Java, and by the LLM prompt exporter to describe the available vocabulary.

### 10.5 Contexts

Functions and statement handlers receive different contexts, because they have different powers.
A function may **read** variables but never write them; only a statement handler may modify the
store, and only as its pattern's postconditions promise.

```java
public interface Context {                          // common
    <T> T service(Class<T> type);
    <T> T service(Class<T> type, String qualifier);
    MathContext mathContext();
    void log(String level, String message);
    void debug(String message);
    void error(String message);                     // throws BubasException
}

public interface FunctionContext extends Context {
    Value argument(String name);
    Value variable(String name);                    // read-only
}

public interface StatementContext extends Context {
    ExpressionArg expression(String name);
    VariableArg   variable(String name);
    LiteralArg    literal(String name);
    BubasType     type(String name);
}
```

Service lookup walks the interpreter first, then the language, so a run-scoped service overrides
a singleton of the same key. The key is the class plus an optional qualifier, defaulting to the
empty string.

Services are invisible to BUBAS. No syntax refers to them and no script can observe one; they
exist only so that shared function definitions in a sealed language can reach per-run state such
as a transaction, a tenant or a correlation id.

### 10.6 Values and arguments

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
    String    name();
    BubasType type();
    boolean   isFinal();
    Value     get();
    void      set(Value value);
    void      set(Object javaValue);
}

public interface LiteralArg {
    Value value();
}
```

`as(Class)` is checked against the registered opaque type rather than blind-casting, so a
mismatch produces a BUBAS diagnostic instead of a `ClassCastException`.

---

## 11. Errors

Every failure aborts the run. BUBAS has no error handling construct, no recovery and no
`ON ERROR`: error policy belongs to the embedding application, in Java.

```java
try {
    Value result = prog.newInterpreter().argument("orderId", 42L).run();
} catch (BubasException e) {
    e.getLine();        // 14
    e.getSourceLine();  // "LET x = a / b"
    e.getMessage();     // "division by zero"
    e.getCause();       // the underlying Java exception, if any
}
```

`ctx.error(message)` throws a `BubasException`; it is a control-flow operation, not a logging
call. Anything thrown by a Java function is wrapped with the line information of the statement
that called it.

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

A `BubasProgram` can be compiled to Java source. The generated code links against the BUBAS
runtime rather than standing alone:

- a custom statement compiles to a callback into the statement registry
- `DECIMAL` division reads the interpreter's `MathContext` at each site
- overflow, bounds and postcondition checks are emitted inline

Compilation therefore buys the removal of AST-walking overhead, not standalone deployment. The
generated code and the interpreter are two implementations of one semantics, so **a conformance
suite is mandatory**: every test program must run through both backends and produce identical
results and identical errors. Divergence between the two is the characteristic bug of this
design, and only the suite will catch it.

---

## 14. Worked Example

```java
BubasLanguage lang = BubasLanguage.builder()
    .defineOpaqueType("Order", Order.class)

    .defineFunction("LOAD_ORDER")
        .parameter("orderId", BubasType.INTEGER)
        .returns(BubasType.opaque("Order"))
        .as(ctx -> ctx.service(OrderService.class)
                      .load(ctx.argument("orderId").asLong()))

    .defineFunction("ORDER_WAS_FOUND")
        .parameter("order", BubasType.opaque("Order"))
        .returns(BubasType.BOOLEAN)
        .as(ctx -> ctx.argument("order").as(Order.class) != null)

    .defineFunction("ORDER_TOTAL")
        .parameter("order", BubasType.opaque("Order"))
        .returns(BubasType.DECIMAL)
        .as(ctx -> ctx.argument("order").as(Order.class).total())

    .defineFunction("LOG_EVENT")
        .parameter("level",   BubasType.STRING)
        .parameter("message", BubasType.STRING)
        .returns(BubasType.VOID)
        .as(ctx -> { log(ctx); return null; })

    .registerService(OrderService.class, orderService)
    .seal();
```

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

    LOG_EVENT "INFO", "approved, total " + total
    RETURN TRUE
END.
```

```java
BubasProgram prog = lang.compile(source);

boolean approved = prog.newInterpreter()
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
DECLARE   LET   FINAL
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
