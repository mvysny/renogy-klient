import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.*

plugins {
    kotlin("jvm") version "1.8.20"
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

    // logging
    implementation("org.slf4j:slf4j-simple:2.0.6")

    // PostgreSQL support
    implementation("org.postgresql:postgresql:42.5.1")
    implementation("com.github.mvysny.vokorm:vok-orm:2.0")
    implementation("com.zaxxer:HikariCP:5.0.1")

    // tests
    testImplementation("com.github.mvysny.dynatest:dynatest:0.24")
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
    applicationDefaultJvmArgs = listOf("-Xmx32m")
}
