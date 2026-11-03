# okay-datalog

Datalog for Scala 3 on the JVM, in Cloudflare Workers (Scala.js) and natively: Horn rules with stratified negation
and comparisons, semi-naive bottom-up evaluation with hash indexes, and proofs — every derived fact's first
derivation, read back as a list of steps (premises before their use, no recursion on a proof's length).

```scala
import okay.datalog.*

val rules = Parse.program("""
  path(X, Y) :- edge(X, Y).
  path(X, Z) :- edge(X, Y), path(Y, Z).
""").toOption.get
val r = Eval.run(rules, Vector(Fact("edge", Vector("a", "b")), Fact("edge", Vector("b", "c"))), proofs = true)
  .toOption.get
r.query(Parse.atom("path(a, X)").toOption.get)        // X = b, X = c
r.proof(Fact("path", Vector("a", "c")))               // edge(b, c) given, path(b, c) ⇐ …, path(a, c) ⇐ …
```

The first handler of one program among several (okay-inlay `specs/datalog.md`): `Eval` here; provenance semirings,
an Inlay handler (the same rules compiled into transformer weights, run on MLX) and SQL to come. Used by symdex
(Datalog over a codebase's index) and okay-facts (rules over a business's data).

```scala
resolvers += "okay-datalog" at "https://okay-effects.github.io/okay-datalog/maven"
libraryDependencies += "io.github.okay-effects" %%% "okay-datalog" % "0.1.0"
```
