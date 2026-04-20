application {
    mainClass.set("io.vanja.cpsat.ch08.MainKt")
}

dependencies {
    // Parity test uses the MiniZinc runner from ch07. Pulled in at test scope
    // only — the main source never touches MZN.
    testImplementation(project(":ch07-mzn-bridge"))
}
