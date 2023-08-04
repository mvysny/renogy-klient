import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.*

plugins {
    kotlin("jvm") version "1.9.0"
    kotlin("plugin.serialization") version "1.9.0"
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
    implementation("com.github.mvysny.kotlin-unsigned-jvm:kotlin-unsigned-jvm:0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-cli:0.3.5")
    implementation("com.fazecast:jSerialComm:2.9.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.0")

    // logging
    implementation("org.slf4j:slf4j-simple:2.0.7")

    // PostgreSQL support
    implementation("org.postgresql:postgresql:42.5.4")
    // connection pooling & liveness testing
    implementation("com.zaxxer:HikariCP:5.0.1")
    // InfluxDB 2 support
    // @todo has shitload of dependencies; replace with http: https://docs.influxdata.com/influxdb/v2.7/write-data/developer-tools/api/
    implementation("com.influxdb:influxdb-client-kotlin:6.10.0")

    // tests
    testImplementation("com.github.mvysny.dynatest:dynatest:0.24")
    testImplementation("org.testcontainers:postgresql:1.17.6")
    testImplementation("com.github.mvysny.vokorm:vok-orm:2.0")
    testImplementation("com.zaxxer:HikariCP:5.0.1")
    testImplementation("org.testcontainers:influxdb:1.17.6")
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        // to see the exceptions of failed tests in Travis-CI console.
        exceptionFormat = TestExceptionFormat.FULL
    }
}

application {
    mainClass.set("MainKt")
    applicationDefaultJvmArgs = listOf("-Xmx20m", "-Xss200k", "-client")
}
