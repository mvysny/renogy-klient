import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.4.20"
    kotlin("plugin.serialization") version "2.4.20"
    application
}

defaultTasks("clean", "build")

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.github.mvysny.kotlin-unsigned-jvm:kotlin-unsigned-jvm:0.3")
    implementation("info.picocli:picocli:4.7.7")
    implementation("com.fazecast:jSerialComm:2.11.4")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    // logging
    implementation("org.slf4j:slf4j-simple:2.0.19")

    // PostgreSQL support
    implementation("org.postgresql:postgresql:42.7.13")
    // connection pooling & liveness testing
    implementation("com.zaxxer:HikariCP:7.1.0")

    // tests
    testImplementation("org.junit.jupiter:junit-jupiter-engine:6.1.3")
    testImplementation(kotlin("test"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.testcontainers:postgresql:1.21.4")
    testImplementation("com.github.mvysny.vokorm:vok-orm:3.2")
    testImplementation("org.testcontainers:influxdb:1.21.4")
    // InfluxDB 2 support
    // has shitload of dependencies; use for tests only
    testImplementation("com.influxdb:influxdb-client-kotlin:8.0.0")
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        // to see the exceptions of failed tests in the CI console.
        exceptionFormat = TestExceptionFormat.FULL
        showCauses = true
    }
}

/**
 * Verifies that the `design/` doc layer is consistent; see AGENTS.md, "Design docs".
 * Bash-only, so it's skipped on Windows.
 */
val designTripwires = tasks.register<Exec>("designTripwires") {
    description = "Checks the design/ doc layer for dangling D_/R_/T_ slugs and oversized AGENTS.md"
    group = "verification"
    commandLine("design/verify_design_tripwires.sh")
    onlyIf { !System.getProperty("os.name").lowercase().startsWith("windows") }
}

tasks.named("check") {
    dependsOn(designTripwires)
}

application {
    mainClass.set("MainKt")
    applicationDefaultJvmArgs = listOf("-Xmx20m", "-Xss200k", "-client")
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
