plugins {
    id("java")
    id("application")

    id("com.github.ben-manes.versions") version "0.50.0"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "hu.garaba"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("jakarta.annotation:jakarta.annotation-api:3.0.0-M1")

    runtimeOnly("org.xerial:sqlite-jdbc:3.44.1.0")

    implementation("org.apache.logging.log4j:log4j-slf4j2-impl:3.0.0-alpha1")
    implementation("org.apache.logging.log4j:log4j-core:3.0.0-alpha1")
    implementation("org.apache.logging.log4j:log4j-jpl:3.0.0-alpha1")

    implementation("com.alibaba:fastjson:2.0.31")
    implementation("org.telegram:telegrambots:6.8.0")
    implementation("org.imgscalr:imgscalr-lib:4.2")


    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

application {
    mainClass = "hu.garaba.Main"
    applicationDefaultJvmArgs = listOf("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.test {
    useJUnitPlatform()
}