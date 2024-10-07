import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    kotlin("jvm") version "2.0.20"
    kotlin("plugin.serialization") version "2.0.20"
    application
}

defaultTasks("clean", "build")

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.github.mvysny.kotlin-unsigned-jvm:kotlin-unsigned-jvm:0.2")
    implementation("info.picocli:picocli:4.7.6")
    implementation("com.fazecast:jSerialComm:2.10.5")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // logging
    implementation("org.slf4j:slf4j-simple:2.0.13")

    // PostgreSQL support
    implementation("org.postgresql:postgresql:42.7.3")
    // connection pooling & liveness testing
    implementation("com.zaxxer:HikariCP:5.1.0")

    // tests
    testImplementation("com.github.mvysny.dynatest:dynatest:0.25")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.testcontainers:postgresql:1.19.8")
    testImplementation("com.github.mvysny.vokorm:vok-orm:3.1")
    testImplementation("org.testcontainers:influxdb:1.19.8")
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
