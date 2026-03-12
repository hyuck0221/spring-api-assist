import org.gradle.kotlin.dsl.named
import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.spring") version "2.2.21"
    kotlin("kapt") version "2.2.21"
    id("org.springframework.boot") version "3.3.5"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.hshim"
version = "0.0.1-SNAPSHOT"
description = "spring-api-assist"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.github.hyuck0221:kotlin-utils:0.0.4")

    // Configuration Processor (IDE autocomplete for application.yml)
    kapt("org.springframework.boot:spring-boot-configuration-processor")

    // Optional: JDBC for DB storage — consumer must add spring-boot-starter-jdbc
    compileOnly("org.springframework.boot:spring-boot-starter-jdbc")

    // Optional: Spring Data for Pageable/Page support in APIDocumentComponent
    compileOnly("org.springframework.data:spring-data-commons")

    // AWS SDK v2 for Supabase S3 (S3-compatible storage)
    implementation(platform("software.amazon.awssdk:bom:2.28.0"))
    implementation("software.amazon.awssdk:s3")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

tasks.jar {
    enabled = true
    archiveClassifier.set("")
}

tasks.named<BootJar>("bootJar") {
    enabled = false
}

tasks.named<Test>("test") {
    enabled = false
}

tasks.withType<Test> {
    useJUnitPlatform()
}