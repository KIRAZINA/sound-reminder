plugins {
    java
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
    // GluonFX plugin for Android native builds (requires GraalVM + Android SDK)
    // Uncomment for Android builds:
    // id("com.gluonhq.gluonfx-gradle-plugin") version "1.0.24"
}

group = "com.soundreminder"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // JSON serialization/deserialization
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.0")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.mockito:mockito-core:5.11.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
    // Open main module packages to test module for reflection
    jvmArgs(
        "--add-opens", "com.soundreminder/com.soundreminder.model=ALL-UNNAMED",
        "--add-opens", "com.soundreminder/com.soundreminder.storage=ALL-UNNAMED",
        "--add-opens", "com.soundreminder/com.soundreminder.sound=ALL-UNNAMED",
        "--add-opens", "com.soundreminder/com.soundreminder.scheduler=ALL-UNNAMED",
        "--add-opens", "com.soundreminder/com.soundreminder.util=ALL-UNNAMED",
        "--add-exports", "com.soundreminder/com.soundreminder.model=ALL-UNNAMED",
        "--add-exports", "com.soundreminder/com.soundreminder.storage=ALL-UNNAMED",
        "--add-exports", "com.soundreminder/com.soundreminder.sound=ALL-UNNAMED",
        "--add-exports", "com.soundreminder/com.soundreminder.scheduler=ALL-UNNAMED",
        "--add-exports", "com.soundreminder/com.soundreminder.util=ALL-UNNAMED"
    )
}

javafx {
    version = "21.0.3"
    modules = listOf("javafx.controls", "javafx.fxml", "javafx.media", "javafx.swing")
}

application {
    mainClass.set("com.soundreminder.Main")
}

// GluonFX configuration for Android native builds
// Requires GraalVM JDK + Android SDK. Uncomment for Android builds.
/*
gluonfx {
    target.set("android")
    attachConfig {
        services.add("lifecycle")
        services.add("storage")
        services.add("notifications")
    }
    appIdentifier = "com.soundreminder"
    appName = "SoundReminder"
    release = true
    verbose = false
    compileArgs.add("-H:+ReportExceptionStackTraces")
}
*/

// Ensure resources are copied properly
tasks.processResources {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

// JAR task with manifest
tasks.jar {
    manifest {
        attributes(
            "Main-Class" to "com.soundreminder.Main",
            "Implementation-Title" to "SoundReminder",
            "Implementation-Version" to version
        )
    }
    // Include all dependencies in a separate fat jar task
}

// Create executable JAR with dependencies
tasks.register<Jar>("fatJar") {
    archiveClassifier.set("all")
    manifest {
        attributes(
            "Main-Class" to "com.soundreminder.Main",
            "Implementation-Title" to "SoundReminder",
            "Implementation-Version" to version
        )
    }
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
