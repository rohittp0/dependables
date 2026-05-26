# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

A multi-module Gradle repository publishing standalone reusable libraries (dependencies) under the group `com.rohittp.dependables`. Mirrors the structure of the sibling [`plugables`](../plugables) repo — same Gradle/Kotlin/publishing conventions — except each subproject produces a consumable library artifact rather than a Gradle plugin.

Published to Maven Central via the Sonatype Central Portal (vanniktech maven-publish).

## Common Commands

```bash
# Build all libraries
./gradlew build

# Build a specific library
./gradlew :<library-name>:build

# Run tests for all libraries
./gradlew test

# Run tests for a specific library
./gradlew :<library-name>:test

# Run a single test class
./gradlew :<library-name>:test --tests "com.rohittp.dependables.<library-name>.<TestClass>"

# Publish a library to local Maven (no signing required)
./gradlew :<library-name>:publishToMavenLocal

# Publish to Maven Central (requires signing key + Sonatype creds in env)
./gradlew :<library-name>:publish
```

## Architecture

The root project applies shared config only — no library code lives at the root. Each library is a fully self-contained subproject with its own `build.gradle.kts` and independent versioning.

**Root `build.gradle.kts`** centralises:
- `group = "com.rohittp.dependables"` for all subprojects
- vanniktech maven-publish wiring: `publishToMavenCentral(automaticRelease = true)`, opt-in `signAllPublications()` when `ORG_GRADLE_PROJECT_signingInMemoryKey` is set
- Opt-out of config cache for `PublishToMavenRepository` tasks (Maven Central publishing isn't config-cache compatible — gradle/gradle#22779)

**Per-subproject `build.gradle.kts`** owns:
- Its own `version = "x.y.z"` (root never sets library versions)
- Its `pom { }` block (description, license, developers, scm) — required for Maven Central
- Kotlin/JVM/Android plugin application and target config

**Repositories** (`google()`, `mavenCentral()`) are configured in `settings.gradle.kts` under `dependencyResolutionManagement` with `repositoriesMode = FAIL_ON_PROJECT_REPOS`. Do not redeclare them in subprojects.

## Adding a New Library

1. Create `<library-name>/` with `build.gradle.kts` and `src/main/kotlin/com/rohittp/dependables/<library-name>/`
2. Add `include(":<library-name>")` to `settings.gradle.kts`
3. Apply `kotlin("jvm")` (or android equivalent) and `id("com.vanniktech.maven.publish")` in the subproject
4. Set `version = "x.y.z"` and a `pom { }` block in the subproject's `build.gradle.kts`
5. Add a row to the top-level `README.md` library table
6. Add a docs page under `docs/<library-name>.html` (mirrors the plugables docs site layout)

## Key Conventions

- JVM target: 21 — `kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_21) } }`
- Each library sets its own version; bump the version in the subproject only when shipping that library
- Publishing credentials come from env vars: `ORG_GRADLE_PROJECT_mavenCentralUsername`, `ORG_GRADLE_PROJECT_mavenCentralPassword`, `ORG_GRADLE_PROJECT_signingInMemoryKey`, `ORG_GRADLE_PROJECT_signingInMemoryKeyPassword`
- `automaticRelease = true` means a successful publish auto-promotes the staging repo — there is no manual "release" click on Sonatype
- Local `publishToMavenLocal` works without any signing key configured