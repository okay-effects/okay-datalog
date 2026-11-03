# AGENTS.md — okay-datalog

The design: ~/work/my/okay-inlay specs/datalog.md (one Datalog program, many handlers). This repository holds the
language, its checks and the handlers that run on every platform.

- Cross-built JVM / Scala.js / Native; `sbt test` runs all three. No unbounded recursion on the size of a program,
  of its data or of a proof (the evaluator recurses only as deep as a rule's atoms).
- Laws as tests: semi-naive equals naive; every proof checks (each step a rule instance over earlier steps).
- Scala 3.3 (usable from 3.9), lines at most 120 characters, English in code and docs, `-Werror`.
- Releases: RELEASING.md (republished under 0.1.0 while unused). Commits: plain messages, no attribution trailers.
