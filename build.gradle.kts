plugins {
    id("com.android.library") version "9.2.0" apply false
    id("com.android.application") version "9.2.0" apply false
    id("com.vanniktech.maven.publish") version "0.33.0" apply false
}

subprojects {
    group = "com.rohittp.dependables"

    // vanniktech maven-publish + Maven Central publish flow isn't config-cache compatible yet:
    // https://github.com/gradle/gradle/issues/22779. Mark publish tasks as opt-out so the rest
    // of the build still benefits from the config cache.
    tasks.withType(org.gradle.api.publish.maven.tasks.PublishToMavenRepository::class.java).configureEach {
        notCompatibleWithConfigurationCache(
            "Maven Central publishing isn't config-cache compatible yet — gradle/gradle#22779."
        )
    }

    // Centralised publishing config — each library only needs its own `pom { }` block.
    // automaticRelease=true flips Sonatype Central Portal deployments to "released" once
    // validation passes, removing the manual click-to-publish step. Local
    // `publishToMavenLocal` is unaffected (no Central Portal involvement).
    plugins.withId("com.vanniktech.maven.publish") {
        extensions.configure<com.vanniktech.maven.publish.MavenPublishBaseExtension> {
            publishToMavenCentral(automaticRelease = true)
            if (System.getenv("ORG_GRADLE_PROJECT_signingInMemoryKey") != null) {
                signAllPublications()
            }
        }
    }
}
