# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

A multi-module Gradle repository publishing standalone reusable libraries (dependencies) under the group `com.rohittp.dependables`. Mirrors the structure of the sibling [`plugables`](../plugables) repo — same Gradle/Kotlin/publishing conventions — except each subproject produces a consumable library artifact rather than a Gradle plugin.

Published as signed, immutable releases to the public Cloudflare R2 Maven repository at
`https://maven.rohittp.com` (vanniktech maven-publish).

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

# Publish to R2 (requires the R2 endpoint, bucket, credentials, and signing key in env)
./gradlew :<library-name>:publishAllPublicationsToR2Repository
```

## Architecture

The root project applies shared config only — no library code lives at the root. Each library is a fully self-contained subproject with its own `build.gradle.kts` and independent versioning.

**Root `build.gradle.kts`** centralises:
- `group = "com.rohittp.dependables"` for all subprojects
- the `R2` S3-compatible Maven repository when `R2_ENDPOINT` and `R2_BUCKET` are set
- opt-in `signAllPublications()` when `ORG_GRADLE_PROJECT_signingInMemoryKey` is set
- config-cache opt-out for remote `PublishToMavenRepository` tasks

**Per-subproject `build.gradle.kts`** owns:
- Its own `version = "x.y.z"` (root never sets library versions)
- Its `pom { }` block (description, license, developers, scm)
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
- R2 publishing uses `R2_ENDPOINT`, `R2_BUCKET`, `R2_ACCESS_KEY_ID`, and `R2_SECRET_ACCESS_KEY`
- Signing uses `ORG_GRADLE_PROJECT_signingInMemoryKey`, `ORG_GRADLE_PROJECT_signingInMemoryKeyId`, and `ORG_GRADLE_PROJECT_signingInMemoryKeyPassword`
- `.github/workflows/publish.yml` rejects a coordinate whose primary POM already exists before publishing
- Local `publishToMavenLocal` works without any signing key configured
