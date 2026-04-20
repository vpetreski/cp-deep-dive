// Chapter 13 — NSP v3: solver tuning and benchmark harness.
//
// Generates synthetic instances at known sizes, solves each with a set of
// SolverParams variants, and writes a CSV to `benchmarks/results/`.
//
// Relies on ch12 for the solver and nsp-core for the data model.

plugins {
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":nsp-core"))
    implementation(project(":ch11-nsp-v1"))
    implementation(project(":ch12-nsp-v2"))
}

application {
    mainClass.set("io.vanja.cpsat.ch13.MainKt")
}
