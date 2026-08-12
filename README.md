# Dependables

Small, focused Android libraries published at [maven.rohittp.com](https://maven.rohittp.com)
under the group
`com.rohittp.dependables`. Each library solves one problem and stays out of the way
of the rest.

| Library | Description | Docs |
|--------|-------------|------|
| [remote-logger](remote-logger/) | Timber tree + push-triggered uploader. Zips databases, shared_prefs, datastore, and recent logs into Firebase Cloud Storage when a `dump_logs` FCM data message arrives. The same zip is exposed via `ExportZipWriter` for in-app "export my data" UIs. | https://rohittp.com/dependables/remote-logger |

## Layout

```
dependables/
├── remote-logger/      # the first library
├── sample-app/         # tiny Android app that wires everything up end-to-end
├── docs/               # static site published to GitHub Pages
└── .github/workflows/  # manually dispatched, immutable R2 releases
```

## Build

```bash
./gradlew build                            # everything
./gradlew :remote-logger:test              # library unit tests
./gradlew :remote-logger:publishToMavenLocal
./gradlew :sample-app:installDebug         # install the demo on a connected device
```

## Use from Gradle

The repository is public; consumers do not need credentials. Add the shared repository in
`settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        maven("https://maven.rohittp.com")
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        maven("https://maven.rohittp.com")
        mavenCentral()
        google()
    }
}
```

`pluginManagement` resolves Gradle plugin IDs hosted in the shared repository.
`dependencyResolutionManagement` resolves ordinary Maven, Android, and KMP coordinates,
including Dependables.

```kotlin
dependencies {
    implementation("com.rohittp.dependables:remote-logger:0.1.0")
}
```

## Sample app

The sample app deliberately ships **without** a `google-services.json`. To exercise the
upload path end-to-end:

1. Drop a real `google-services.json` into `sample-app/`.
2. Add `id("com.google.gms.google-services")` to `sample-app/build.gradle.kts`.
3. Send a `dump_logs` FCM data message to the topic `logdump_<ANDROID_ID>`.

The "Force dump now" button enqueues the same `LogDumpWorker` that the FCM handler does, so
you can validate the local zip path without any push setup.
