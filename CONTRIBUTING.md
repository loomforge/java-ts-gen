# Contributing to Java-TS-Gen

Thanks for your interest in contributing. JTG is intentionally small and focused; changes that match that scope are welcome.

---

## Prerequisites

- **JDK 17** (see the parent `pom.xml`: `java.version`)
- **Maven 3.8+** (3.9.x is used in CI and matches the version pinned in the parent POM for reference)

---

## Clone and build

```bash
git clone https://github.com/loomforge/java-ts-gen.git
cd java-ts-gen
mvn install
```

### Modules

| Module              | Role                                                                |
|---------------------|---------------------------------------------------------------------|
| `jtg-annotation/`   | `@TsRecord` — no third-party dependencies, `RetentionPolicy.SOURCE` |
| `jtg-maven-plugin/` | `jtg:generate` goal, `RecordParser`, `TypeMapper`, `TsEmitter`      |

---

## Tests and checks

Run the full unit test suite:

```bash
mvn test
```

Tests live under `jtg-maven-plugin/src/test/java/`. New behaviour should include tests that fail without your change and pass with it.

Before opening a PR, run a full build the same way CI does:

```bash
mvn -B package
```

The **Java CI with Maven** workflow (`.github/workflows/maven.yml`) runs on pushes and pull requests to `main` and executes that command.

The parent POM configures **Spotless** with Palantir Java Format. It runs during the `compile` phase (`spotless:check`). If formatting fails locally, apply it with:

```bash
mvn spotless:apply
```

Then run `mvn package` again.

---

## How to contribute

### Reporting a bug

Open a [GitHub issue](https://github.com/loomforge/java-ts-gen/issues) with:

- JTG / artifact version
- Minimal Java source (record + annotation) that reproduces the problem
- Expected vs actual generated TypeScript

### Suggesting a feature

Open an issue before large or ambiguous work. The [Roadmap](README.md#roadmap) lists planned directions; PRs aligned with those items are especially welcome.

### Pull requests

1. Fork the repository and create a branch, e.g. `feat/your-feature`.
2. Implement the change with tests and formatting (`mvn spotless:apply` if needed).
3. Run `mvn -B package` (or at least `mvn verify`) and ensure everything passes.
4. Open a PR against `main` with a clear description of what changed and why.

---

## Code conventions

- Match the style of surrounding code; Spotless enforces Java formatting on build.
- Keep commits focused: one logical change per commit when practical.
- Prefer imperative, concise commit subjects, e.g. `Add enum support to TypeMapper`.

---

## License

By contributing, you agree your contributions will be licensed under the [MIT License](LICENSE).
