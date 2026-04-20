# Contributing to cp-deep-dive

Thanks for considering a contribution. This project is primarily a long-form, opinionated teaching resource on constraint programming with OR-Tools CP-SAT, but the `libs/cpsat-kt/` Kotlin DSL is a first-class artifact and welcomes improvements.

## Ways to contribute

- **Bug reports** — open a GitHub issue with a minimal reproducer.
- **Teaching corrections** — typos, unclear explanations, broken examples in `docs/chapters/*.md`. Small PRs welcome.
- **`cpsat-kt` improvements** — new global-constraint helpers, performance fixes, documentation. See [`libs/cpsat-kt/README.md`](libs/cpsat-kt/README.md).
- **NSP app features** — the end-to-end app under `apps/` is educational, but we accept genuine feature PRs as long as they stay within the scope of the locked spec at [`specs/nsp-app/`](specs/nsp-app/).
- **Benchmarks** — reproducible runs on new hardware or with new parameter settings are welcome under [`benchmarks/`](benchmarks/).

## Ground rules

- **Discuss before big changes.** If the change touches more than ~200 lines across multiple files, open an issue first to agree on scope.
- **Spec before code for the NSP app.** If you want a new app feature, first propose an amendment to `specs/nsp-app/`. See the lock/amendment procedure in [`specs/nsp-app/README.md`](specs/nsp-app/README.md).
- **No breaking changes to `cpsat-kt` without a major bump.** Follow semver.
- **Dual-language parity.** If you change a chapter example in one language, update the other. The whole repo is built on Python + Kotlin-via-`cpsat-kt` parity.
- **No vendored code.** Depend on published artifacts. Exception: `.mzn` model files are allowed to live in-repo.

## Code style

| Area | Tooling |
|---|---|
| Python | `ruff check` + `mypy` (non-strict for chapter code, strict for library and app source) |
| Kotlin | `ktlint` + `detekt` (configs shipped with each Gradle project) |
| TypeScript | `eslint` (flat config) + `tsc --noEmit` |
| Markdown | No trailing whitespace, no emojis, line length ~100 when practical |

Run the relevant linter before opening a PR. CI will check.

## Commit messages

`<area>: <imperative summary>`.

Examples:
- `cpsat-kt: add Regular constraint helper`
- `docs/ch07: fix broken MiniZinc install link`
- `apps/web: fix Gantt tooltip overflow on mobile`

Keep the subject under ~70 characters. Add a body paragraph for any non-trivial change explaining the why.

## Developer Certificate of Origin (DCO)

By contributing, you certify that you have the right to submit the work under the project's Apache License 2.0 and that you agree to the [Developer Certificate of Origin](https://developercertificate.org/). Add a `Signed-off-by` line to every commit:

```
Signed-off-by: Your Name <your@email.example>
```

`git commit -s` adds this automatically.

## Pull-request checklist

- [ ] The change follows the scope discussed in the issue (if applicable).
- [ ] New or changed code has tests.
- [ ] `ruff` / `ktlint` / `eslint` all pass locally.
- [ ] Relevant tests pass locally (`uv run pytest`, `./gradlew test`, `npm test`).
- [ ] If a chapter example changed: both Python and Kotlin updated.
- [ ] If the NSP spec changed: amendment log updated in `specs/nsp-app/README.md`.
- [ ] Commits are signed off (DCO).

## Security

Please do not file security issues as public GitHub issues. See [`SECURITY.md`](SECURITY.md) for responsible-disclosure steps.

## Code of Conduct

This project follows the [Contributor Covenant](CODE_OF_CONDUCT.md). By participating, you agree to uphold it.
