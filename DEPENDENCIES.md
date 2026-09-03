# Dependency Policy

Ruler is a low-level Java library published to Maven Central. Every dependency
we take on is inherited by every application that depends on us, so the cost of
adding one is paid mostly by other people. Please read this before opening a
pull request that adds or materially upgrades a dependency.

## Where we are today

The library currently declares three compile-scope dependencies:

* `com.fasterxml.jackson.core:jackson-databind` — parsing JSON events
* `ch.randelshofer:fastdoubleparser` — fast `double` parsing
* `com.google.code.findbugs:jsr305` — nullability annotations

JUnit and JMH are `test` scope and do not propagate to consumers.

That list is short on purpose. The checklist below is what a reviewer will walk
through when a change proposes adding a dependency. You do not need to write an
essay — a short note in the pull request covering these points is enough.

## Before adding a dependency

1. **Ask whether we need one at all.** A lot of what Ruler does is a few dozen
   lines of plain Java. If the thing you want fits in a small self-contained
   class, prefer writing it. A library that drags in a large class hierarchy to
   replace one small data structure is a bad trade however good the library is.

2. **Establish that the payoff is common enough to matter.** A dependency is
   easier to justify when it improves a path that users hit routinely. If the
   gain only appears in an unusual edge case, that is a strong argument against
   taking it on.

3. **Show the benefit with numbers, and say where they came from.** Run
   [`scripts/perf-compare.sh main HEAD`](scripts/perf-compare.sh) and include
   the delta table in the pull request (see
   [Performance](README.md#performance) for alternatives). State *which*
   benchmark you ran — a number without a named benchmark cannot be checked.

4. **Report the cost, not just the benefit.** How many classes and how much size
   does this actually add, and what transitive dependencies come along with it?
   "It is a popular library" is not a measurement.

5. **Check the quality signals.** Is it actively maintained? Does it have
   usable documentation? Is it already relied on by other performance-sensitive
   libraries? Those are stronger evidence than popularity on its own.

## Governance, security, and licensing

6. **License compatibility.** The dependency's license must be compatible with
   this project's Apache-2.0 license and redistributable by our consumers. Note
   the license in the pull request.

7. **Ongoing security burden.** A dependency is a commitment rather than a
   one-off import: it has to be tracked for CVEs, patched, and upgraded,
   including the transitive tree it brings with it. If a vulnerability lands in
   it, we own the response. Say what you expect to be monitored, and by whom.

8. **Record the decision.** Walk through the points above in the pull request
   description so the reasoning is captured somewhere findable. Dependency
   decisions are ultimately a maintainer call, and a written rationale keeps the
   next person from re-litigating the same question. See
   [GOVERNANCE.md](GOVERNANCE.md).

## Upgrading an existing dependency

The same checklist applies in miniature: say what changed, whether it is a
patch, minor, or major move, and what you ran to confirm nothing regressed —
`mvn test`, plus the benchmarks where performance could be affected.
