import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
    id("com.vanniktech.maven.publish")
}

version = "0.1.0"

android {
    namespace = "com.rohittp.dependables.remotelogger"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { it.useJUnit() }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

dependencies {
    api(libs.timber)
    api(libs.work.runtime.ktx)
    implementation(libs.coroutines.android)

    // Firebase pieces — declared as `api` so consumers get Storage / Messaging on their classpath.
    // The BOM is applied so we never accidentally pin a specific patch version.
    api(platform(libs.firebase.bom))
    api(libs.firebase.storage)
    api(libs.firebase.messaging)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}

mavenPublishing {
    // R2 publishing and signing are configured centrally in the root build.gradle.kts.

    // AGP 9's bundled Dokka (1.4.32) cannot read Kotlin 2.3 metadata — skip the javadoc jar
    // entirely. The sources jar still ships alongside the Android publication.
    configure(
        com.vanniktech.maven.publish.AndroidSingleVariantLibrary(
            variant = "release",
            sourcesJar = true,
            publishJavadocJar = false,
        )
    )

    pom {
        name.set("remote-logger")
        description.set("Timber file logger + push-notification-triggered upload of logs to Firebase Cloud Storage.")
        url.set("https://github.com/rohittp0/dependables")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
            }
        }
        developers {
            developer {
                id.set("rohittp0")
                name.set("Rohit T P")
                url.set("https://rohittp.com")
            }
        }
        scm {
            url.set("https://github.com/rohittp0/dependables")
            connection.set("scm:git:git://github.com/rohittp0/dependables.git")
            developerConnection.set("scm:git:ssh://git@github.com/rohittp0/dependables.git")
        }
    }
}
