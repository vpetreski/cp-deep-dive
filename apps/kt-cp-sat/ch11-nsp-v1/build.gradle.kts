// Chapter 11 — Nurse Scheduling v1: hard constraints only.
//
// This chapter introduces the NSP decision-variable grid, the eight hard
// constraints HC-1..HC-8, and verifies OPTIMAL/FEASIBLE on toy-01 and toy-02.
// Everything reusable lives in the `nsp-core` module — this chapter is the
// thinnest possible wrapper that loads JSON, solves, and prints ASCII.

plugins {
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":nsp-core"))
}

application {
    mainClass.set("io.vanja.cpsat.ch11.MainKt")
}
