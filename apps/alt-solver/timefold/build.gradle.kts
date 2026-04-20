plugins {
    kotlin("jvm") version "2.3.20"
    kotlin("plugin.allopen") version "2.3.20"
    kotlin("plugin.serialization") version "2.3.20"
    application
}

group = "io.vanja.altsolver"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25)
    }
}

// Timefold instantiates annotated classes via reflection and demands they be
// non-final. The all-open plugin opens just the classes carrying Timefold's
// annotations, keeping the rest of the module idiomatically final.
allOpen {
    annotation("ai.timefold.solver.core.api.domain.entity.PlanningEntity")
    annotation("ai.timefold.solver.core.api.domain.solution.PlanningSolution")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("ai.timefold.solver:timefold-solver-core:1.16.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    // Timefold pulls in SLF4J; wire a concrete backend so logs go somewhere.
    implementation("ch.qos.logback:logback-classic:1.5.18")

    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
    testImplementation("io.kotest:kotest-assertions-core:5.9.1")
}

application {
    mainClass.set("io.vanja.altsolver.timefold.MainKt")
    applicationDefaultJvmArgs = listOf(
        // Timefold on JDK 25 relies on a handful of internal modules still
        // accessible under `--add-opens`. These mirror what Timefold's own
        // examples ship with.
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.base/java.util=ALL-UNNAMED",
    )
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    maxHeapSize = "2g"
    jvmArgs(
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.base/java.util=ALL-UNNAMED",
    )
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = project.hasProperty("verbose")
    }
}
