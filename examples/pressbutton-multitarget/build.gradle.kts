plugins {
    java
    id("br.com.cloudmc.cloudscript-module")
}

group = "com.bezouro.modules.examples"
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

cloudScriptModule {
    architecture.set("multi-runtime")
    moduleName.set("PressButton")
    stubsVersion.set("latest.release")
    runtimes.set(listOf(
        "desktop15",
        "desktop18",
        "minicraft15",
        "minicraft18",
        "microcraft"
    ))
}
