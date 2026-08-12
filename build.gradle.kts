import org.gradle.api.credentials.AwsCredentials
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository

plugins {
    id("com.android.library") version "9.2.0" apply false
    id("com.android.application") version "9.2.0" apply false
    id("com.vanniktech.maven.publish") version "0.33.0" apply false
}

val r2Endpoint = providers.environmentVariable("R2_ENDPOINT")
val r2Bucket = providers.environmentVariable("R2_BUCKET")

// Gradle uses this property for custom S3-compatible endpoints. Keep ordinary local
// builds usable when publishing credentials are intentionally absent.
r2Endpoint.orNull?.let { System.setProperty("org.gradle.s3.endpoint", it) }

subprojects {
    group = "com.rohittp.dependables"

    plugins.withId("maven-publish") {
        // The R2 repository is available only in publishing environments. This keeps builds,
        // tests, and publishToMavenLocal independent of remote credentials.
        if (r2Endpoint.isPresent && r2Bucket.isPresent) {
            extensions.configure<PublishingExtension> {
                repositories {
                    maven {
                        name = "R2"
                        url = uri("s3://${r2Bucket.get()}")

                        credentials(AwsCredentials::class) {
                            accessKey = providers.environmentVariable("R2_ACCESS_KEY_ID").orNull
                            secretKey = providers.environmentVariable("R2_SECRET_ACCESS_KEY").orNull
                        }
                    }
                }
            }
        }
    }

    tasks.withType(PublishToMavenRepository::class.java).configureEach {
        notCompatibleWithConfigurationCache(
            "Remote Maven publishing is not configuration-cache compatible."
        )
    }

    // Centralised signing config — each library retains its own publication and POM setup.
    plugins.withId("com.vanniktech.maven.publish") {
        extensions.configure<com.vanniktech.maven.publish.MavenPublishBaseExtension> {
            if (System.getenv("ORG_GRADLE_PROJECT_signingInMemoryKey") != null) {
                signAllPublications()
            }
        }
    }
}
