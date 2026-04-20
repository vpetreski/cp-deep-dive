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
