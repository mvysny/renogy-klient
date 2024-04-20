import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.*

plugins {
    kotlin("jvm") version "1.9.23"
    kotlin("plugin.serialization") version "1.9.23"
    application
}

defaultTasks("clean", "build")

repositories {
    mavenCentral()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation("com.github.mvysny.kotlin-unsigned-jvm:kotlin-unsigned-jvm:0.2")
    implementation("info.picocli:picocli:4.7.5")
    implementation("com.fazecast:jSerialComm:2.10.4")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

    // logging
    implementation("org.slf4j:slf4j-simple:2.0.12")

    // PostgreSQL support
    implementation("org.postgresql:postgresql:42.7.2")
    // connection pooling & liveness testing
    implementation("com.zaxxer:HikariCP:5.1.0")

    // tests
    testImplementation("com.github.mvysny.dynatest:dynatest:0.25")
    testImplementation("org.testcontainers:postgresql:1.19.6")
    testImplementation("com.github.mvysny.vokorm:vok-orm:3.1")
    testImplementation("org.testcontainers:influxdb:1.19.6")
    // InfluxDB 2 support
    // has shitload of dependencies; use for tests only
    testImplementation("com.influxdb:influxdb-client-kotlin:6.11.0")
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        // to see the exceptions of failed tests in the CI console.
        exceptionFormat = TestExceptionFormat.FULL
    }
}

application {
    mainClass.set("MainKt")
    applicationDefaultJvmArgs = listOf("-Xmx20m", "-Xss200k", "-client")
}
