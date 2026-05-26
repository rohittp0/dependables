import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
}

android {
    namespace = "com.rohittp.dependables.sample"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.rohittp.dependables.sample"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

dependencies {
    implementation(project(":remote-logger"))

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity.ktx)
}
