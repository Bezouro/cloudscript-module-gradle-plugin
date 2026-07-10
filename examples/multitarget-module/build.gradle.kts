plugins {
    java
}

group = "br.com.cloudmc.examples"
version = "1.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(8)
}

val targetName = (findProperty("target") as String?) ?: "microcraft"
val adapterDir = when (targetName) {
    "desktop15" -> "adapter-desktop15"
    "desktop18" -> "adapter-desktop18"
    "minicraft" -> "adapter-minicraft"
    "microcraft" -> "adapter-microcraft"
    else -> throw GradleException("Unknown target '$targetName'. Use desktop15, desktop18, minicraft or microcraft.")
}

sourceSets.named("main") {
    java.srcDir("src/common/java")
    java.srcDir("src/adapter-shared/java")
    java.srcDir("src/$adapterDir/java")
}

tasks.jar {
    archiveBaseName.set("smarthello-$targetName")
}

// Real projects should add the CloudScript/MacroMod stubs for the chosen target.
// This sample focuses on source layout and adapter boundaries.
