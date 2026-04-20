// Common conventions for every chapter subproject.
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "application")

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
    }
}

// Re-expose the version catalog inside `subprojects { }` closures.
val Project.libs: org.gradle.accessors.dm.LibrariesForLibs
    get() = extensions.getByType(org.gradle.accessors.dm.LibrariesForLibs::class.java)
