import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.2.20"
    kotlin("plugin.serialization") version "2.2.20"
    application
}

defaultTasks("clean", "build")

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.github.mvysny.kotlin-unsigned-jvm:kotlin-unsigned-jvm:0.3")
    implementation("info.picocli:picocli:4.7.6")
    implementation("com.fazecast:jSerialComm:2.10.5")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // logging
    implementation("org.slf4j:slf4j-simple:2.0.17")

    // PostgreSQL support
    implementation("org.postgresql:postgresql:42.7.3")
    // connection pooling & liveness testing
    implementation("com.zaxxer:HikariCP:5.1.0")

    // tests
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.11.0")
    testImplementation(kotlin("test"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.testcontainers:postgresql:1.20.4")
    testImplementation("com.github.mvysny.vokorm:vok-orm:3.1")
    testImplementation("org.testcontainers:influxdb:1.20.4")
    // InfluxDB 2 support
    // has shitload of dependencies; use for tests only
    testImplementation("com.influxdb:influxdb-client-kotlin:7.1.0")
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

tasks.withType<KotlinCompile> {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
