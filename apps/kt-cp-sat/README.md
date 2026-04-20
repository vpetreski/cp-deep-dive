# kt-cp-sat — Kotlin CP-SAT chapter apps

Gradle composite build hosting one subproject per chapter of the
[cp-deep-dive](../../README.md) Kotlin track. Each chapter is a runnable
`application` — same input/output contract as its Python twin under
`../py-cp-sat/`.

## Layout

```
apps/kt-cp-sat/
├── settings.gradle.kts       <- composite build: includeBuild("../../libs/cpsat-kt")
├── build.gradle.kts          <- conventions applied to every subproject
├── gradle/libs.versions.toml <- version catalog (pins Kotlin + cpsat-kt)
├── ch02-hello/               <- raw OR-Tools Java demo (motivational pain)
├── ch04-puzzles/             <- stub
├── ch05-optimization/        <- stub
├── ch06-globals/             <- stub
├── ch09-jobshop/             <- stub
├── ch10-shifts/              <- stub
├── ch11-nsp-v1/              <- stub
├── ch12-nsp-v2/              <- stub
└── ch13-nsp-v3/              <- stub
```

## Run a chapter

```bash
./gradlew :ch02-hello:run
```

Output for `ch02-hello`:

```
Found: x=4, y=0
Status: OPTIMAL
...
```

## Requirements

- JDK 25+ (toolchain-resolved automatically on first build)
- Gradle 9.x (use the bundled wrapper)
- macOS arm64, Linux x64, or Windows x64 (OR-Tools native libraries)

## Why a composite build?

The library `libs/cpsat-kt` is consumed via `includeBuild(...)` so changes
there flow directly into this workspace without republishing. For a fully
pinned build, either bump `cpsat-kt` in `gradle/libs.versions.toml` and
publish to Maven Local, or remove the `includeBuild(...)` line.
