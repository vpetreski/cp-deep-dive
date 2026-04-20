// Shared data model + JSON I/O + constraint helpers for the NSP chapters 11-13.
//
// This is the one "utility" module — its job is to centralize the Instance /
// Schedule / Assignment / Violation types, plus a few common helpers
// (build the decision-variable grid, load toy JSON, render ASCII schedule)
// so each chapter module can focus on the CP-SAT modeling being taught.

plugins {
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(libs.cpsat.kt)
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)
}
