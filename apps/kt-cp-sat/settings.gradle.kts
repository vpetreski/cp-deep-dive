rootProject.name = "kt-cp-sat"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        mavenLocal()   // for cpsat-kt if we build it locally
    }
}

// Use the sibling cpsat-kt library as a composite build so changes in
// libs/cpsat-kt are picked up without republishing.
includeBuild("../../libs/cpsat-kt")

// Each chapter is a separate subproject so we can do `:ch02-hello:run`.
include(
    ":ch02-hello",
    ":ch04-puzzles",
    ":ch05-optimization",
    ":ch06-globals",
    ":ch07-mzn-bridge",
    ":ch08-mzn-port",
    ":ch09-jobshop",
    ":ch10-shifts",
    ":nsp-core",
    ":ch11-nsp-v1",
    ":ch12-nsp-v2",
    ":ch13-nsp-v3",
)
