// Common conventions for every chapter subproject.
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    // nsp-core is a library — chapters are applications.
    if (name != "nsp-core") {
        apply(plugin = "application")
    }

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(25)
        }
    }

    extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
        jvmToolchain(25)
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25)
        }
    }

    repositories {
        mavenCentral()
        mavenLocal()
    }

    dependencies {
        add("implementation", rootProject.libs.cpsat.kt)
        add("implementation", rootProject.libs.kotlinx.coroutines.core)
        add("testImplementation", rootProject.libs.kotest.runner.junit5)
        add("testImplementation", rootProject.libs.kotest.assertions.core)
        add("testImplementation", rootProject.libs.kotlinx.coroutines.test)
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        // OR-Tools native loader mutates JVM state — run serially.
        maxParallelForks = 1
        maxHeapSize = "2g"
        systemProperty("kotest.framework.parallelism", "1")
        testLogging {
            events("passed", "failed", "skipped")
            showStandardStreams = project.hasProperty("verbose")
        }
    }
}

// Re-expose the version catalog inside `subprojects { }` closures.
val Project.libs: org.gradle.accessors.dm.LibrariesForLibs
    get() = extensions.getByType(org.gradle.accessors.dm.LibrariesForLibs::class.java)
