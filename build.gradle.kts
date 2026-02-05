plugins {
    kotlin("jvm") version "1.9.22"
    kotlin("plugin.serialization") version "1.9.22"
    application
}

group = "com.firebasemanager"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // Telegram Bot API
    implementation("com.github.pengrad:java-telegram-bot-api:6.9.0")
    
    // Firebase Admin SDK
    implementation("com.google.firebase:firebase-admin:9.2.0")
    
    // Logging
    implementation("ch.qos.logback:logback-classic:1.4.11")
    
    // Kotlinx Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    
    // Coroutines (for suspend RulesService)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    
    // OkHttp for Firebase REST (reliable TLS)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("com.firebasemanager.ApplicationKt")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "17"
    }
}
