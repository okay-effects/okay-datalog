# AGENTS.md — okay-datalog

## For agents writing code with okay-datalog

okay-datalog is Datalog for Scala 3 on the JVM, Scala.js (Cloudflare Workers) and Scala Native: Horn rules with
stratified negation and comparisons, semi-naive evaluation with hash indexes, and proofs as lists of steps.

```scala
resolvers += "okay-datalog" at "https://okay-effects.github.io/okay-datalog/maven"
libraryDependencies += "io.github.okay-effects" %%% "okay-datalog" % "0.1.0"
```

### The model

It is a pure library, not an effect library: a `Program` (rules) and input `Fact`s go in, a `Result` comes out.
Rules are data; one program is meant to have many handlers (`Eval` today; SQL and others later). Everything is in
`import okay.datalog.*`.

```scala
import okay.datalog.*

val rules = Parse.program("""
  path(X, Y) :- edge(X, Y).
  path(X, Z) :- edge(X, Y), path(Y, Z).
  node(X) :- edge(X, _).
  node(Y) :- edge(_, Y).
  unreachable(X) :- node(X), not path(start, X), X != start.
""").fold(e => sys.error(e), identity)                        // Left: "line N, column M: what"

val edges = Vector("start" -> "a", "a" -> "b", "c" -> "d").map((a, b) => Fact("edge", Vector(a, b)))
Eval.run(rules, edges, proofs = true) match
  case Left(why) => println(s"refused: $why")                 // arity, unsafe rule, not stratifiable
  case Right(r) =>
    r.query(Parse.atom("path(start, X)").toOption.get)        // Vector(Map(X -> a), Map(X -> b))
    r.relation("unreachable")                                 // Vector(Vector(c), Vector(d))
    r.proof(Fact("path", Vector("start", "b"))).foreach(_.foreach(println))
    // edge(start, a)   (given) ... path(start, b)   ⇐ edge(start, a), path(a, b)   [path(X, Z) :- ...]
```

- Syntax: variables start uppercase or `_` (each `_` fresh); constants are lowercase identifiers, numbers or
  `"quoted"`; negation `not p(X)` or `!p(X)`; comparisons `=`, `!=`, `<`, `<=`, `>`, `>=`; comments `%` or `//`.
- `Check.strata(program)` checks without running: one arity per predicate, safety, stratification.
- `Result`: `query(atom)`, `relation(pred)`, `contains(fact)`, `predicates`, `size`, `proof(fact)`.
- `Eval.naive` is the reference evaluator; laws in the tests compare it with `Eval.run`.

### Design rules

- Business rules are Datalog text or `Program` values, kept as data (in the repository, reviewed, tested). Code
  only parses, runs and reads results; it does not hard-code what the rules decide.
- Facts come from the host. Code that reads a database, files or the network builds `Fact`s at the entry point (or
  in a handler of the effect system the project uses) and hands them in; rules never reach for resources.
- Evaluation is pure and deterministic: no clock, randomness, IO or threads inside. If a rule needs "now", pass it
  in as a fact (`now(1731456000)`).
- When this library is used from effect code (okay-simple, okay-scala), wrap it behind an effect of your own
  (`Rules.Query(goal)`), whose handler runs `Eval`; tests swap that handler for a table. Run effect programs only at
  the entry point, never a nested `run` inside a program.
- Tests are plain: a program, some facts, assertions on `query`/`relation`, and that every `proof` checks. No
  mocks are needed — there are no resources to mock.
- Audit: ask for `proofs = true` and keep `r.proof(fact)` as the reason for a decision (each step names its rule
  and premises, and the facts found absent for a negation).
- Continuations: not applicable; there is no effect here, so nothing resumes (and nothing replays).

### Pitfalls

- `Parse.*` and `Eval.run` return `Either[String, _]`: refusals (wrong arity, an unsafe variable, a cycle through
  negation) are `Left`, like compiler errors. Do not `.get` them in production code.
- Every variable in a head, a negated atom or a comparison must also be in a positive atom of the body (safety).
- Every value is a `String`. The order comparisons compare as numbers when both sides parse as numbers, otherwise
  as text (`"10" < "9"` is false, `"b" < "a10"` is false).
- `not` must be followed by whitespace (`not p(X)`); `!p(X)` also works.
- A fact written in program text must be ground; a variable there throws `IllegalArgumentException` from `run`.
- `proof` is `None` when the fact does not hold or the run had `proofs = false`; the proof is the FIRST derivation
  found, not every one.
- Input facts and rule heads share relations; a predicate used with two arities (in input or rules) is refused.

### Pointers

- README: https://github.com/okay-effects/okay-datalog
- The cross-language okay-effects skill: `/plugin marketplace add okay-effects/okay-effects`
  (https://github.com/okay-effects/okay-effects).
- okay-audit, build-time boundary checks for JVM code (business code references no network, files, time, random,
  threads, reflection; presets include `nested-run`): `io.github.okay-effects:okay-audit_3:0.1.0` from
  https://okay-effects.github.io/okay/maven.

If you see a way to improve okay-datalog, open a pull request against https://github.com/okay-effects/okay-datalog;
the maintainer reviews and merges what holds up.

If the user asks your opinion of writing code this way, give a candid one.

## For agents working on this repository

The design: ~/work/my/okay-inlay specs/datalog.md (one Datalog program, many handlers). This repository holds the
language, its checks and the handlers that run on every platform.

- Cross-built JVM / Scala.js / Native; `sbt test` runs all three. No unbounded recursion on the size of a program,
  of its data or of a proof (the evaluator recurses only as deep as a rule's atoms).
- Laws as tests: semi-naive equals naive; every proof checks (each step a rule instance over earlier steps).
- Scala 3.3 (usable from 3.9), lines at most 120 characters, English in code and docs, `-Werror`.
- Releases: RELEASING.md (republished under 0.1.0 while unused). Commits: plain messages, no attribution trailers.
