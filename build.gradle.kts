plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    id("io.ktor.plugin") version "3.0.3"
    id("com.gradleup.shadow") version "8.3.5"
}

group = "com.caterktor.live"
version = "0.1.0"

application {
    mainClass.set("com.caterktor.live.ApplicationKt")
}

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/ktor/eap")
}

val ktor_version = "3.0.3"
val logback_version = "1.5.12"

dependencies {
    implementation("io.ktor:ktor-server-core:$ktor_version")
    implementation("io.ktor:ktor-server-cio:$ktor_version")
    implementation("io.ktor:ktor-server-sse:$ktor_version")
    implementation("io.ktor:ktor-server-content-negotiation:$ktor_version")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
    implementation("io.ktor:ktor-server-cors:$ktor_version")
    implementation("io.ktor:ktor-server-call-logging:$ktor_version")
    implementation("io.ktor:ktor-server-status-pages:$ktor_version")
    implementation("io.ktor:ktor-server-default-headers:$ktor_version")

    implementation("io.ktor:ktor-server-html-builder:$ktor_version")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    implementation("ch.qos.logback:logback-classic:$logback_version")
    implementation("net.logstash.logback:logstash-logback-encoder:8.0")

    testImplementation("io.ktor:ktor-server-test-host:$ktor_version")
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.0.21")
}

tasks.shadowJar {
    archiveFileName.set("app.jar")
    mergeServiceFiles()
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
