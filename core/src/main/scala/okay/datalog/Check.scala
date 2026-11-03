package okay.datalog

import scala.collection.mutable

/**
 * WHAT A PROGRAM MUST BE BEFORE IT RUNS, refused like a compiler error: every predicate one arity; every rule safe
 * (each variable of its head, of a negated atom, of a comparison bound by a positive atom of its body); and
 * stratifiable (no cycle through negation). The strata: groups of predicates in dependency order, each computed
 * after the ones it depends on — a negated predicate always in an earlier stratum.
 */
object Check:

  /** the predicates of each stratum, in the order they are computed */
  def strata(p: Program, inputs: Map[String, Int] = Map.empty): Either[String, Vector[Vector[String]]] =
    for
      _ <- arities(p, inputs)
      _ <- safety(p)
      s <- stratify(p)
    yield s

  def arities(p: Program, inputs: Map[String, Int]): Either[String, Map[String, Int]] =
    val seen = mutable.LinkedHashMap.from(inputs)
    val atoms = p.rules.flatMap(r => r.head +: r.body.collect {
      case Literal.Pos(a) => a
      case Literal.Neg(a) => a
    })
    atoms.find(a => seen.get(a.pred).exists(_ != a.arity) || { seen.getOrElseUpdate(a.pred, a.arity); false }) match
      case Some(a) => Left(s"${a.pred} is used with ${a.arity} arguments and with ${seen(a.pred)}")
      case None => Right(seen.toMap)

  def safety(p: Program): Either[String, Unit] =
    p.rules.iterator.map { r =>
      val bound = r.body.collect { case Literal.Pos(a) => a.vars }.flatten.toSet
      val needed = r.head.vars ++ r.body.flatMap {
        case Literal.Pos(_) => Vector.empty
        case other => other.vars
      }
      needed.find(v => !bound(v)).map(v => s"unsafe rule, $v is not bound by a positive atom: $r")
    }.collectFirst { case Some(why) => why }.toLeft(())

  /** Tarjan's strongly connected components, iterative (no recursion on the program's size) */
  def stratify(p: Program): Either[String, Vector[Vector[String]]] =
    val preds = mutable.LinkedHashSet.empty[String]
    val edges = mutable.LinkedHashMap.empty[String, mutable.ArrayBuffer[(String, Boolean)]] // head -> (body, negated)
    for r <- p.rules do
      preds += r.head.pred
      val out = edges.getOrElseUpdate(r.head.pred, mutable.ArrayBuffer.empty)
      for l <- r.body do l match
        case Literal.Pos(a) => preds += a.pred; out += (a.pred -> false)
        case Literal.Neg(a) => preds += a.pred; out += (a.pred -> true)
        case _ => ()
    val index = mutable.HashMap.empty[String, Int]
    val low = mutable.HashMap.empty[String, Int]
    val onStack = mutable.HashSet.empty[String]
    val stack = mutable.Stack.empty[String]
    val comps = mutable.ArrayBuffer.empty[Vector[String]]  // in reverse topological order: dependencies first
    var counter = 0
    for start <- preds if !index.contains(start) do
      // the call stack of the recursive algorithm, as data: (node, next edge to look at)
      val work = mutable.Stack((start, 0))
      index(start) = counter; low(start) = counter; counter += 1
      stack.push(start); onStack += start
      while work.nonEmpty do
        val (v, next) = work.pop()
        val out = edges.getOrElse(v, mutable.ArrayBuffer.empty)
        if next < out.size then
          work.push((v, next + 1))
          val w = out(next)._1
          if !index.contains(w) then
            index(w) = counter; low(w) = counter; counter += 1
            stack.push(w); onStack += w
            work.push((w, 0))
          else if onStack(w) then low(v) = low(v) min index(w)
        else
          if low(v) == index(v) then
            val comp = mutable.ArrayBuffer.empty[String]
            var w = ""
            while { w = stack.pop(); onStack -= w; comp += w; w != v } do ()
            comps += comp.toVector
          if work.nonEmpty then
            val parent = work.top._1
            low(parent) = low(parent) min low(v)
    val compOf = comps.zipWithIndex.flatMap((c, i) => c.map(_ -> i)).toMap
    val badNeg = edges.iterator.flatMap((h, out) => out.collect { case (b, true) if compOf(b) == compOf(h) => (h, b) })
    badNeg.nextOption() match
      case Some((h, b)) => Left(s"not stratifiable: $h depends on not $b, and $b on $h")
      case None => Right(comps.toVector)
