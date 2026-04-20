plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.dokka)
    `maven-publish`
}

group = "io.vanja"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
    withSourcesJar()
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        // JVM 25 target — we target the LTS and require it at runtime.
        // Downgrade to 21 if a consumer needs it (supported via version catalog).
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25)
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

dependencies {
    api(libs.ortools.java)
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.property)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    useJUnitPlatform()
    // OR-Tools tests must run serially — the native loader mutates JVM state.
    maxParallelForks = 1
    // Show native solver output only when requested via -Pverbose.
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = project.hasProperty("verbose")
    }
    // Give CP-SAT enough heap for non-trivial tests.
    maxHeapSize = "2g"
    // Kotest sometimes needs extra time for JNI native-library load on first test.
    systemProperty("kotest.framework.parallelism", "1")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        allWarningsAsErrors.set(false)
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            pom {
                name.set("cpsat-kt")
                description.set("Idiomatic Kotlin DSL over Google OR-Tools CP-SAT")
                url.set("https://github.com/vanjap/cp-deep-dive")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("vanjap")
                        name.set("Vanja Petreski")
                        email.set("vanja@petreski.co")
                    }
                }
            }
        }
    }
}
