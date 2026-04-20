// Chapter 12 — NSP v2: soft constraints and multi-objective optimization.
//
// Builds on top of ch11 by adding SC-1..SC-5 penalty terms to the hard model
// and minimizing either a weighted sum or a lexicographic sequence of
// objectives.

plugins {
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":nsp-core"))
    implementation(project(":ch11-nsp-v1"))
}

application {
    mainClass.set("io.vanja.cpsat.ch12.MainKt")
}
