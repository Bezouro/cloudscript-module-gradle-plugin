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
    id("br.com.cloudmc.cloudscript-module") version "0.4.6"
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

When a module must compile strictly against the desktop workspace jar, keep
CloudMC stubs only for validation:

```kotlin
cloudScriptModule {
    setupWorkspace.set(true)
    useWorkspaceClasspath.set(true)
    addCloudMcStubDependency.set(false)
}
```

## API versions

The plugin currently supports:

- API 10: Minecraft 1.5.2, MCP `net.minecraft.src.*` development names.
- API 18: Minecraft 1.8, MCP/deobfuscated modern package names.
- API 26: Minecraft 1.12.1 CloudMC/native modules. Desktop obfuscation and
  `setupCloudScriptWorkspace` are not bundled for API 26 yet.

Unsupported API versions fail during Gradle configuration with a clear error.
For API 26, use published stubs or your own `compileOnly` jars and set
`deployDesktop.set(false)` when deploying.

## API 10 Naming Mode

API 10 defaults to the classic Minecraft 1.5.2 MCP package layout:

```kotlin
cloudScriptModule {
    apiVersion.set(10)
    modernMinecraftNames.set(false)
}
```

That means developers import classes such as:

```java
import net.minecraft.src.WorldClient;
```

If you prefer to develop 1.5 modules using CloudMC-style modern packages, enable
the bridge mode:

```kotlin
cloudScriptModule {
    apiVersion.set(10)
    modernMinecraftNames.set(true)
}
```

Then developers can import:

```java
import net.minecraft.client.multiplayer.WorldClient;
```

Internally the plugin downloads the official 1.5.2 client jar, remaps it
notch -> MCP, then applies the embedded bridge mapping MCP 1.5 -> CloudMC-style
packages to the development library. The CloudMC jar is emitted with modern
names, while the desktop jar is still obfuscated back to notch names.

## Main tasks

```text
setupCloudScriptWorkspace
buildDesktopModule
buildCloudMcModule
buildCloudScriptModule
validateCloudScriptModule
generateCloudMcNativeMetadata
deployCloudScriptModule
```

`buildCloudScriptModule` is the normal local release task. It produces:

```text
build/libs/<moduleName>-Api<api>-desktop.jar
build/libs/<moduleName>-Api<api>-cloudmc.jar
```

The desktop jar is obfuscated back to Minecraft notch names. The CloudMC jar is
remapped where needed, enriched with native-image metadata and validated against
the latest public CloudMC stubs. `moduleName` defaults to the Gradle project
name and can be set explicitly when the project name already contains an API
suffix.

## GraalVM native metadata

`generateCloudMcNativeMetadata` runs automatically as part of the CloudMC module
pipeline. It keeps the public CloudMC artifact name unchanged:

```text
build/libs/<moduleName>-Api<api>-cloudmc.jar
```

The task adds metadata used by Minicraft native-image builds:

```text
META-INF/cloudmc/cloudscript-module.classes
META-INF/cloudmc/cloudscript-api<api>.classes
META-INF/native-image/cloudmc/<module>-api<api>/reflect-config.json
META-INF/native-image/cloudmc/<module>-api<api>/resource-config.json
```

Registerable classes are detected by bytecode hierarchy. The class name does
not need to start with `ScriptAction`; extending CloudScript's action/provider
base classes or implementing Macro Keybind interfaces is enough.

Resource metadata is generated for non-class resources packaged in the module.
If your module creates classes by reflection or reads resources whose paths are
computed dynamically, declare the extra metadata explicitly:

```kotlin
cloudScriptModule {
    nativeReflectClasses.add("com.example.internal.CreatedByReflection")
    nativeResourcePatterns.add("\\Qassets/example/config.json\\E")
}
```

You can disable automatic resource inclusion or all native metadata when needed:

```kotlin
cloudScriptModule {
    nativeIncludeModuleResources.set(false)
    nativeMetadata.set(false)
}
```

Native compatibility is a build-time contract. Desktop Minecraft and JVM-based
CloudMC can still load downloaded module jars dynamically. GraalVM native images
cannot load new Java bytecode after compilation, so third-party modules must be
included in the Minicraft native-image build classpath.

## Deploy

`deployCloudScriptModule` builds, validates and uploads the enabled variants to
the CloudScript backend:

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

`deployDesktop` and `deployCloudMc` control which artifacts are built,
validated and uploaded. When `deployCloudMc` is enabled, the task uploads the
final jar produced by `generateCloudMcNativeMetadata`:

```text
build/libs/<moduleName>-Api<api>-cloudmc.jar
```

It does not upload the intermediate jar produced by `remapCloudMcModule`.

API 26 is CloudMC-only in this plugin release:

```kotlin
cloudScriptModule {
    apiVersion.set(26)
    deployDesktop.set(false)
    deployCloudMc.set(true)
}
```

## Compatibility notes

For API 10 and API 18, existing module projects that use the public tasks
continue to build desktop Minecraft and JVM CloudMC artifacts the same way:

```text
buildCloudMcModule
buildCloudScriptModule
deployCloudScriptModule
```

The CloudMC jar now includes additional native-image metadata under
`META-INF/cloudmc` and `META-INF/native-image`. This does not change the module
runtime contract for desktop Minecraft or JVM CloudMC, but it does change the
byte-for-byte jar contents. If your release flow stores checksums, signatures or
other artifact hashes, generate them from the final `build/libs` jar.

`remapCloudMcModule` is now an intermediate pipeline step. Automation outside
Gradle should consume the final `build/libs/<moduleName>-Api<api>-cloudmc.jar`
artifact or call `buildCloudMcModule`, `buildCloudScriptModule` or
`deployCloudScriptModule`.

## Example projects

Ready-to-use zipped examples are published in this repository under
`dist/examples`:

- `cloudscript-api10-example.zip`
- `cloudscript-api18-example.zip`

Each zip is a standalone Gradle project. Extract it and run:

```powershell
.\gradlew.bat buildCloudScriptModule
```

The repository also includes
[`examples/multitarget-module`](../examples/multitarget-module), a source-layout
blueprint for modules that keep behavior in common code and isolate Desktop
1.5, Desktop 1.8, CloudMC/Minicraft and Microcraft differences behind small
runtime adapters. The current plugin still builds one API version at a time;
that example documents the target structure the plugin should automate next.

For a concrete action implementation, see
[`examples/pressbutton-multitarget`](../examples/pressbutton-multitarget). It
implements `pressbutton(<0..2>)` with common validation plus runtime adapters
for Desktop 1.5, Desktop 1.8, Minicraft 1.5, Minicraft 1.8 and Microcraft.

## Multi-runtime modules

Existing projects keep using the default architecture:

```kotlin
cloudScriptModule {
    architecture.set("normal")
    apiVersion.set(18)
}
```

For the adapter layout, opt into the new architecture and list the supported
runtimes:

```kotlin
cloudScriptModule {
    architecture.set("multi-runtime")
    moduleName.set("PressButton")
    runtimes.set(listOf(
        "desktop15",
        "desktop18",
        "minicraft15",
        "minicraft18",
        "microcraft"
    ))
}
```

The plugin creates one source set per runtime from `src/common/java` plus
`src/<runtime>/java`, imports the matching CloudMC and CloudScript stubs for
that runtime, and creates build tasks like `buildDesktop15Module`,
`buildMinicraft18Module` and `buildMicrocraftModule`.

Minecraft obfuscation is applied only to `desktop15` and `desktop18`.
`minicraft15`, `minicraft18` and `microcraft` are packaged as-is.
