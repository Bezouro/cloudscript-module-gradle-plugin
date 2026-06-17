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
    apiVersion.set(18)
    moduleName.set("hello-api18")
    setupWorkspace.set(true)
    useWorkspaceClasspath.set(true)
    stubsVersion.set("latest.release")
}
