package okay.datalog

import scala.util.Random

class DatalogSuite extends munit.FunSuite:

  private def prog(s: String): Program = Parse.program(s).fold(e => fail(e), identity)
  private def edges(es: (String, String)*): Vector[Fact] = es.map((a, b) => Fact("edge", Vector(a, b))).toVector
  private val closure = prog("""
    % reachability
    path(X, Y) :- edge(X, Y).
    path(X, Z) :- edge(X, Y), path(Y, Z).
  """)

  test("the text syntax: variables, constants, strings, negation, comparisons, comments"):
    val p = prog("""p(X, "a \"q\"", b, 42) :- q(X), not r(X), !s(X), X != b, X = X. // done""")
    assertEquals(p.rules.size, 1)
    val r = p.rules.head
    assertEquals(r.head.args(1), Term.Const("a \"q\""))
    assertEquals(r.body.collect { case l: Literal.Neg => l }.size, 2)
    assertEquals(r.body.collect { case l: Literal.Cmp => l.op }, Vector("!=", "="))
    assertEquals(Parse.program(p.toString).map(_.toString), Right(p.toString))
    assert(Parse.program("p(X) :- q(X)").isLeft)          // no final dot
    assert(Parse.atom("""affects(X, "okay/T.join().")""").isRight)

  test("refused like a compiler: arities, unsafe rules, a cycle through negation"):
    assert(Eval.run(prog("p(X) :- q(X). p(X, Y) :- q(X), q(Y).")).left.exists(_.contains("arguments")))
    assert(Eval.run(prog("p(X, Y) :- q(X).")).left.exists(_.contains("unsafe")))
    assert(Eval.run(prog("p(X) :- q(X), not r(Y).")).left.exists(_.contains("unsafe")))
    assert(Eval.run(prog("p(X) :- q(X), not p(X).")).left.exists(_.contains("not stratifiable")))
    assert(Eval.run(prog("p(X) :- q(X), not r(X). r(X) :- q(X), not p(X).")).left.exists(_.contains("stratifiable")))

  test("transitive closure: a chain and a cycle"):
    val chain = Eval.run(closure, edges((1 until 30).map(i => (s"n$i", s"n${i + 1}"))*)).toOption.get
    assertEquals(chain.relation("path").size, 29 * 30 / 2)
    val cycle = Eval.run(closure, edges("a" -> "b", "b" -> "c", "c" -> "a")).toOption.get
    assertEquals(cycle.relation("path").size, 9)
    assertEquals(cycle.query(Parse.atom("path(a, X)").toOption.get).map(_("X")).toSet, Set("a", "b", "c"))

  test("stratified negation: what cannot be reached"):
    val p = closure ++ prog("""
      node(X) :- edge(X, _).
      node(Y) :- edge(_, Y).
      unreachable(X) :- node(X), not path(start, X), X != start.
    """)
    val r = Eval.run(p, edges("start" -> "a", "a" -> "b", "c" -> "d")).toOption.get
    assertEquals(r.relation("unreachable").map(_.head).toSet, Set("c", "d"))

  test("a program's own facts, and rules over constants alone"):
    val r = Eval.run(prog("""
      parent(ann, bob). parent(bob, cid).
      anc(X, Y) :- parent(X, Y).
      anc(X, Z) :- parent(X, Y), anc(Y, Z).
      root(ann) :- ann = ann.
    """)).toOption.get
    assert(r.contains(Fact("anc", Vector("ann", "cid"))))
    assert(r.contains(Fact("root", Vector("ann"))))

  test("the law: semi-naive equals naive, on random graphs and programs with negation"):
    val p = closure ++ prog("""
      node(X) :- edge(X, _).
      node(Y) :- edge(_, Y).
      sym(X, Y) :- path(X, Y), path(Y, X).
      oneway(X, Y) :- path(X, Y), not path(Y, X).
      same_gen(X, X) :- node(X).
      same_gen(X, Y) :- edge(A, X), same_gen(A, B), edge(B, Y).
    """)
    val r = Random(7)
    for round <- 1 to 40 do
      val n = 3 + r.nextInt(14)
      val es = for a <- 0 until n; b <- 0 until n if r.nextDouble() < 0.15 yield s"v$a" -> s"v$b"
      val fast = Eval.run(p, edges(es*)).toOption.get
      val slow = Eval.naive(p, edges(es*)).toOption.get
      for pred <- Vector("path", "sym", "oneway", "same_gen") do
        assertEquals(fast.relation(pred).toSet, slow.relation(pred).toSet, s"round $round, $pred")

  test("proofs: every step a rule instance over earlier steps or given facts; a long chain without recursion"):
    val n = 600
    val r = Eval.run(closure, edges((1 until n).map(i => (s"n$i", s"n${i + 1}"))*), proofs = true).toOption.get
    val goal = Fact("path", Vector("n1", s"n$n"))
    val steps = r.proof(goal).get
    assertEquals(steps.last.fact, goal)
    val seen = scala.collection.mutable.HashSet.empty[Fact]
    for s <- steps do
      s.by match
        case None => assert(r.contains(s.fact))
        case Some(d) =>
          assert(d.premises.forall(seen), s"a premise before its use: $s")
          // the rule instantiated by the premises gives the fact
          val atoms = d.rule.body.collect { case Literal.Pos(a) => a }
          val b = atoms.zip(d.premises).flatMap((a, f) => a.args.zip(f.args).collect { case (Term.Var(v), x) => v -> x })
          assertEquals(b.groupMap(_._1)(_._2).values.map(_.distinct.size).toSet, Set(1))
          val bm: Map[String, String] = b.toMap
          val head: Vector[String] = d.rule.head.args.map { case Term.Var(v) => bm(v); case Term.Const(c) => c }
          assertEquals(Fact(d.rule.head.pred, head), s.fact)
      seen += s.fact
    assert(r.proof(Fact("path", Vector(s"n$n", "n1"))).isEmpty)

/** the order comparisons: by number when both sides are numbers, by text otherwise */
class OrderSuite extends munit.FunSuite:
  test("<, <=, >, >= — numbers by value, words by text"):
    val p = Parse.program("""big(X) :- n(X, V), V > 100. small(X) :- n(X, V), V <= 100. early(X) :- w(X, W), W < "m".""").toOption.get
    val facts = Vector(Fact("n", Vector("a", "120")), Fact("n", Vector("b", "80")), Fact("n", Vector("c", "100")), Fact("n", Vector("d", "1000")),
      Fact("w", Vector("x", "apple")), Fact("w", Vector("y", "zebra")))
    val r = Eval.run(p, facts).toOption.get
    assertEquals(r.query(Parse.atom("big(X)").toOption.get).map(_("X")).toSet, Set("a", "d"))
    assertEquals(r.query(Parse.atom("small(X)").toOption.get).map(_("X")).toSet, Set("b", "c"))
    assertEquals(r.query(Parse.atom("early(X)").toOption.get).map(_("X")).toSet, Set("x"))
