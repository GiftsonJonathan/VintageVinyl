plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure Kotlin/JVM module: the audio engine has no Android dependencies so that it can be
// unit-tested (and rendered offline to WAV files) on a desktop JVM.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation(libs.junit)
}
