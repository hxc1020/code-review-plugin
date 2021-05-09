@file:Suppress("UnstableApiUsage")
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.jetbrains.intellij") version "0.7.2"
    java
    kotlin("jvm") version "1.4.32"
}

group = "red.hxc"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.3")
    implementation("com.github.salomonbrys.kotson:kotson:2.5.0")
    implementation("io.joshworks.unirest:unirest-java:1.8.0") {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

// See https://github.com/JetBrains/gradle-intellij-plugin/
intellij {
    version = "2021.1"
    setPlugins("git4idea", "com.intellij.tasks")
}
tasks.getByName<Test>("test") {
    useJUnitPlatform()
}
tasks.getByName<org.jetbrains.intellij.tasks.PatchPluginXmlTask>("patchPluginXml") {
    changeNotes("""
      Add change notes here.<br>
      <em>most HTML tags may be used</em>""")
}

java{
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "11"
}

tasks.publishPlugin{
    setToken(System.getenv("ORG_GRADLE_PROJECT_intellijPublishToken"))
}
