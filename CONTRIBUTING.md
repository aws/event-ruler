# Contributing Guidelines

Thank you for your interest in contributing to our project. Whether it's a bug report, new feature, correction, or additional
documentation, we greatly value feedback and contributions from our community.

Please read through this document before submitting any issues or pull requests to ensure we have all the necessary
information to effectively respond to your bug report or contribution.


## Reporting Bugs/Feature Requests

We welcome you to use the GitHub issue tracker to report bugs or suggest features.

When filing an issue, please check existing open, or recently closed, issues to make sure somebody else hasn't already
reported the issue. Please try to include as much information as you can. Details like these are incredibly useful:

* A reproducible test case or series of steps
* The version of our code being used
* Any modifications you've made relevant to the bug
* Anything unusual about your environment or deployment


## Contributing via Pull Requests
Contributions via pull requests are much appreciated. Before sending us a pull request, please ensure that:

1. You are working against the latest source on the *main* branch.
2. You check existing open, and recently merged, pull requests to make sure someone else hasn't addressed the problem already.
3. You open an issue to discuss any significant work - we would hate for your time to be wasted.
4. If your change adds a dependency or changes the scope of one, you have read [Dependencies](#dependencies) below and your pull request answers its questions; if it upgrades one, you have read that section's [Upgrades and vulnerabilities](#upgrades-and-vulnerabilities).

To send us a pull request, please:

1. Fork the repository.
2. Modify the source; please focus on the specific change you are contributing.
   1. We recommend you break any large changes into smaller commits.
   2. If you also reformat all the code, it will be hard for us to focus on your change.
   3. Where possible stay consistent with the current code-style, patterns, and documentation language.
   4. Any changes (however minor, major, or ground-breaking) are welcomed, though reviewing them in isolation of any other changes helps us review them faster.
3. Ensure local tests pass. Add new tests for any functionality you add / changed.
4. Ensure there are no performance regressions. The easiest way is [`scripts/perf-compare.sh main HEAD`](https://github.com/aws/event-ruler/blob/main/scripts/perf-compare.sh) which runs the benchmark on both refs and prints a noise-aware delta table. See the [Performance section in the README](https://github.com/aws/event-ruler/blob/main/README.md#performance) for details and alternatives ([`Benchmarks.java`](https://github.com/aws/event-ruler/blob/main/src/test/software/amazon/event/ruler/Benchmarks.java) for quick single-shot runs, [`StableBenchmarks.java`](https://github.com/aws/event-ruler/blob/main/src/test/software/amazon/event/ruler/StableBenchmarks.java) when you want to invoke the averaged harness directly).
5. Commit to your fork using clear commit messages. [PULL_REQUEST_TEMPLATE.md](https://github.com/aws/event-ruler/blob/main/.github/PULL_REQUEST_TEMPLATE.md) shows the template we follow.
6. Send us a pull request, answering any default questions in the pull request interface.
7. Pay attention to any automated CI failures reported in the pull request, and stay involved in the conversation.

GitHub provides additional document on [forking a repository](https://help.github.com/articles/fork-a-repo/) and
[creating a pull request](https://help.github.com/articles/creating-a-pull-request/).


## Dependencies

Event Ruler ("Ruler" from here on) is a low-level library of about ten thousand lines of plain Java.
Every entry in [`pom.xml`](pom.xml) not marked `<scope>test</scope>` or `<optional>true</optional>` (Maven's
"compile" scope) lands on the classpath of every application that uses Ruler unless that application
excludes it, and each of those applications inherits its size, its transitive tree, its license, and every
vulnerability found in it for as long as they ship it. Some consumers also repackage the library and each
transitive dependency in their own build systems, so a new dependency is new work for people who never asked
for it. The dependency list is short on purpose, each dependency carries a comment in `pom.xml` saying why
it is there, and the default answer to a new one is no.

### The bar

A dependency clears the bar when Ruler cannot reasonably do the job itself and the payoff reaches users
routinely, not only in an edge case. "Reasonably" is measured in code we would write and maintain: a data
structure or an algorithm that fits in one self-contained class is written here, however good the library
that offers it. A JSON parser does not fit in one class - that is why Jackson is here.

### Proposing one

Open an issue first: a new dependency is exactly the kind of significant work that item 3 of the checklist
above asks you to discuss before writing code. Then answer these in the pull request description, briefly:

1. **Why not write it.** What the dependency does for us, and why the equivalent code does not belong in
   this repository.
2. **Who benefits.** Which user-facing path improves, and whether users hit it routinely.
3. **The benefit, measured.** For a performance benefit, the delta table from
   `scripts/perf-compare.sh main HEAD` or a named `StableBenchmarks` run - a number without a named
   benchmark cannot be checked. For a correctness benefit, the tests that show it.
4. **The cost, measured.** Jar size, class count, and the full transitive tree (`mvn dependency:tree`)
   with each library's license. Popularity is not a cost measurement; these numbers are.
5. **Health.** Release cadence, response to reported issues, whether it runs on our Java baseline
   (`jdk.version` in `pom.xml`), and whether other low-level libraries already rely on it.
6. **License.** The dependency's license, and every license in its transitive tree, must be on the allow
   list in [amazon-ospo/dependency-review-config](https://github.com/amazon-ospo/dependency-review-config/blob/main/default/dependency-review-config.yml).
   The GPL family (GPL, LGPL, AGPL) is not on it.

### Scope matters

* **Test scope** (`<scope>test</scope>`) reaches no consumer, so the bar's payoff test does not apply. A
  test dependency answers questions 1, 5 and 6 only. For question 6, its license has to permit use in our
  build but does not have to be on the allow list, because nothing of it ships to consumers. Keep the set
  small anyway.
* **Annotation-only libraries** (for example `jsr305`) are dependencies too. One is justified by the tools
  that recognize its annotations, not by a benchmark, so it answers questions 1, 5 and 6. A new one is
  declared `<optional>true</optional>` in its `pom.xml` entry, so that applications using Ruler do not pick
  it up by default.
* **Changing a scope** so that a dependency reaches consumers - test to compile, or removing `<optional>` -
  makes it a new dependency for them: answer all six questions. A change in the other direction - compile
  to test, or adding `<optional>` - needs only its reason.
* **Build plugins** never reach consumers; they are reviewed for what they do to the build, not by this
  checklist.

### Upgrades and vulnerabilities

Dependabot opens weekly upgrade pull requests ([`.github/dependabot.yml`](.github/dependabot.yml)). An
upgrade pull request states whether the version change is patch, minor, or major; it does not answer the
six questions. A major change, or any change to Jackson or fastdoubleparser (the libraries Ruler calls
while matching an event), includes a `perf-compare` run - by the contributor for a hand-made upgrade, by
the merging maintainer for a Dependabot one.

A vulnerability published against a dependency is Ruler's problem to fix: the maintainers upgrade or
replace the dependency and release, so that consumers do not need to override our versions to be safe. A
GitHub issue naming the advisory is welcome; anything not yet public goes through
[Security issue notifications](#security-issue-notifications) below, never a public issue.

### Decision and record

Taking a dependency is a maintainer decision ([GOVERNANCE.md](GOVERNANCE.md)). The answers above stay in
the pull request so the reasoning is findable later, and the `pom.xml` entry carries a one-line comment
saying why the dependency is there.


## Finding contributions to work on
Looking at the existing issues is a great way to find something to contribute on. As our projects, by default, use the default GitHub issue labels (enhancement/bug/duplicate/help wanted/invalid/question/wontfix), looking at any 'help wanted' issues is a great place to start.


## Code of Conduct
This project has adopted the [Amazon Open Source Code of Conduct](https://aws.github.io/code-of-conduct).
For more information see the [Code of Conduct FAQ](https://aws.github.io/code-of-conduct-faq) or contact
opensource-codeofconduct@amazon.com with any additional questions or comments.

## Governance

This project has governance model can be found in [GOVERNANCE.md](https://github.com/aws/event-ruler/blob/main/GOVERNANCE.md).

## Security issue notifications
If you discover a potential security issue in this project we ask that you notify AWS/Amazon Security via our [vulnerability reporting page](http://aws.amazon.com/security/vulnerability-reporting/). Please do **not** create a public github issue.


## Licensing

See the [LICENSE](LICENSE) file for our project's licensing. We will ask you to confirm the licensing of your contribution.
