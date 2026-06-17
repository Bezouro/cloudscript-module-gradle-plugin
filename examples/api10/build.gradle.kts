plugins {
    java
    id("br.com.cloudmc.cloudscript-module") version "0.4.0"
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

cloudScriptModule {
    apiVersion.set(10)
    moduleName.set("hello-api10")
    setupWorkspace.set(true)
    useWorkspaceClasspath.set(true)
    // Set true to compile API 10 against CloudMC-style modern packages
    // such as net.minecraft.client.multiplayer.WorldClient.
    modernMinecraftNames.set(false)
    stubsVersion.set("latest.release")
}
