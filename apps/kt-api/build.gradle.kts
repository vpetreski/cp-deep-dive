plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

group = "io.vanja"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25)
    }
    explicitApi()
}

dependencies {
    implementation(libs.cpsat.kt)
    implementation(libs.nsp.core)

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.call.id)
    implementation(libs.ktor.server.sse)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.request.validation)
    implementation(libs.ktor.server.metrics.micrometer)

    implementation(libs.logback.classic)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.java.time)
    implementation(libs.sqlite.jdbc)

    implementation(libs.micrometer.registry.prometheus)
    implementation(libs.snakeyaml)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.assertions.json)
    testImplementation(libs.kotlinx.coroutines.core)
}

application {
    mainClass.set("io.vanja.nspapi.AppKt")
}

// Copy apps/shared/openapi.yaml into resources at build time so /openapi.yaml
// can serve the spec verbatim from the classpath.
val copyOpenApi = tasks.register<Copy>("copyOpenApi") {
    from(rootProject.layout.projectDirectory.dir("../shared")) {
        include("openapi.yaml")
    }
    into(layout.buildDirectory.dir("generated-resources/openapi"))
}

sourceSets {
    main {
        resources {
            srcDir(copyOpenApi.map { it.destinationDir })
        }
    }
}

tasks.named<ProcessResources>("processResources") {
    dependsOn(copyOpenApi)
}

tasks.test {
    useJUnitPlatform()
    maxParallelForks = 1
    maxHeapSize = "2g"
    systemProperty("kotest.framework.parallelism", "1")
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = project.hasProperty("verbose")
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        allWarningsAsErrors.set(false)
    }
}
