# CloudScript Module Gradle Plugin usage

This guide shows how to create, build, validate, obfuscate and deploy modules
with the public CloudScript module plugin.

## Requirements

- JDK 17 or newer to run Gradle.
- Internet access on the first build.
- A CloudScript session token only when using `deployCloudScriptModule`.

The generated module bytecode targets Java 8.

## Plugin setup

Add the plugin repository to `settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://bezouro.github.io/cloudscript-module-gradle-plugin/maven")
    }
}
```

Do not use `repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)` in
example modules unless you also declare the CloudScript and CloudMC Maven
repositories in settings. The plugin adds those public repositories so it can
resolve `latest.release` stubs.

Apply the plugin in `build.gradle.kts`:

```kotlin
plugins {
    java
    id("br.com.cloudmc.cloudscript-module") version "0.3.0"
}

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
    moduleName.set("hello-world")
    setupWorkspace.set(true)
    useWorkspaceClasspath.set(true)
    stubsVersion.set("latest.release")
}
```

## API versions

The plugin currently supports:

- API 10: Minecraft 1.5.2, MCP `net.minecraft.src.*` development names.
- API 18: Minecraft 1.8, MCP/deobfuscated modern package names.

Unsupported API versions fail during Gradle configuration with a clear error.

## Main tasks

```text
setupCloudScriptWorkspace
buildDesktopModule
buildCloudMcModule
buildCloudScriptModule
validateCloudScriptModule
deployCloudScriptModule
```

`buildCloudScriptModule` is the normal local release task. It produces:

```text
build/libs/<project>-Api<api>-desktop.jar
build/libs/<project>-Api<api>-cloudmc.jar
```

The desktop jar is obfuscated back to Minecraft notch names. The CloudMC jar is
remapped where needed and validated against the latest public CloudMC stubs.

## Deploy

`deployCloudScriptModule` builds, validates and uploads both variants to the
CloudScript backend:

```powershell
$env:CLOUDSCRIPT_TOKEN = "<session-token>"
.\gradlew.bat deployCloudScriptModule
```

You can also pass the token as a Gradle property:

```powershell
.\gradlew.bat deployCloudScriptModule -PcloudScriptToken="<session-token>"
```

Deploy settings:

```kotlin
cloudScriptModule {
    deployBaseUrl.set("https://cloudscript.bezouro.com.br")
    deployDesktop.set(true)
    deployCloudMc.set(true)
}
```

## Example projects

Ready-to-use zipped examples are published in this repository under
`dist/examples`:

- `cloudscript-api10-example.zip`
- `cloudscript-api18-example.zip`

Each zip is a standalone Gradle project. Extract it and run:

```powershell
.\gradlew.bat buildCloudScriptModule
```
