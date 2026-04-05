# Contributing to Java-TS-Gen

Thanks for your interest in contributing! JTG is a small, focused tool — contributions that align with its scope are very welcome.

---

## Getting Started

**Prerequisites:** Java 17+, Maven 3.8+

```bash
git clone https://github.com/loomforge/java-ts-gen.git
cd java-ts-gen
mvn install
```

The project has two modules:

| Module | Purpose |
|---|---|
| `jtg-annotation/` | `@TsRecord` annotation (zero deps, source retention) |
| `jtg-maven-plugin/` | `jtg:generate` Mojo, parser, type mapper, and emitter |

---

## Running Tests

```bash
mvn test
```

Tests live in `jtg-maven-plugin/src/test/java/`. All changes must pass the existing suite. New behaviour must be covered by new tests.

---

## How to Contribute

### Reporting a Bug
Open a [GitHub Issue](https://github.com/loomforge/java-ts-gen/issues) with:
- JTG version
- Minimal Java record that reproduces the problem
- Expected vs. actual generated TypeScript

### Suggesting a Feature
Open an issue before writing code. Check the [Roadmap](README.md#roadmap) — items there are already planned and PRs for them are welcome.

### Submitting a Pull Request
1. Fork the repo and create a branch: `git checkout -b feat/your-feature`
2. Make your changes with tests
3. Run `mvn verify` and confirm everything passes
4. Open a PR against `main` with a clear description of what and why

---

## Code Conventions

- Follow the style of the surrounding code (standard Java conventions)
- Keep commits focused — one logical change per commit
- Commit messages: short imperative summary, e.g. `Add enum support to TypeMapper`

---

## License

By contributing, you agree your contributions will be licensed under the [MIT License](LICENSE).
