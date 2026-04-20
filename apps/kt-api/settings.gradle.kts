rootProject.name = "kt-api"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        mavenLocal()
    }
}

// Pull in the sibling cpsat-kt library via composite build so changes flow
// immediately without a separate publish step.
includeBuild("../../libs/cpsat-kt")

// Re-use the nsp-core library (domain model + ModelBuilder) from the
// kt-cp-sat learning modules via composite build with a substitution so we
// can depend on it as `io.vanja:nsp-core` from build.gradle.kts.
includeBuild("../kt-cp-sat") {
    dependencySubstitution {
        substitute(module("io.vanja:nsp-core")).using(project(":nsp-core"))
    }
}
