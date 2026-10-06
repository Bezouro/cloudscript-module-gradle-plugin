# CloudScript Module Gradle Plugin

Build helper for CloudScript modules that target Desktop Minecraft and CloudMC.

Full usage guide: [docs/usage.md](docs/usage.md).

Standalone example project zips:

- [dist/examples/cloudscript-api10-example.zip](dist/examples/cloudscript-api10-example.zip)
- [dist/examples/cloudscript-api18-example.zip](dist/examples/cloudscript-api18-example.zip)

Source-layout example for future multi-runtime modules:

- [examples/multitarget-module](examples/multitarget-module)
- [examples/pressbutton-multitarget](examples/pressbutton-multitarget)

Architecture modes:

- `normal` keeps the existing single-API workflow and task names.
- `multi-runtime` creates one target per configured runtime, imports the
  matching stubs, obfuscates only Desktop Minecraft targets and leaves
  Minicraft/Microcraft jars as-is.

Current behavior:

- API 10 / Minecraft 1.5: compiles normally, remaps `net/minecraft/src/*` class
  references to CloudMC package names using the embedded 1.5 bridge SRG, then
  validates against the latest CloudMC API 10 stubs. It also remaps the desktop
  jar back to notch names using the embedded Minecraft 1.5.2 SRG.
- API 18 / Minecraft 1.8: no naming remap is applied, but the output jar is
  validated against the latest CloudMC API 18 stubs. It also remaps the desktop
  jar back to notch names using the embedded Minecraft 1.8 SRG.
- API 26 / Minecraft 1.12.1: CloudMC/native modules are validated and enriched
  with native-image metadata. Desktop obfuscation is not bundled for API 26 yet;
  use `deployDesktop.set(false)`.

Example module build:

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://bezouro.github.io/cloudscript-module-gradle-plugin/maven")
    }
}
```

```kotlin
plugins {
    java
    id("br.com.cloudmc.cloudscript-module") version "0.4.11"
}

cloudScriptModule {
    apiVersion.set(18)
    setupWorkspace.set(true)
    useWorkspaceClasspath.set(true)
    moduleName.set("hello-world")
}
```

API 10 can use classic MCP 1.5 names by default, or CloudMC-style modern names:

```kotlin
cloudScriptModule {
    apiVersion.set(10)
    modernMinecraftNames.set(true)
}
```

With this enabled, the workspace jar is remapped from notch -> MCP and then
through the embedded bridge mapping MCP 1.5 -> CloudMC-style packages. The
CloudMC jar keeps modern names and the desktop jar is still obfuscated back to
notch names.

The plugin downloads/remaps the Minecraft dev jar, adds CloudScript/Macro
Keybind/LiteLoader dev stubs and CloudMC stubs as `compileOnly`, then produces
jar names from `cloudScriptModule.moduleName`:

- `build/libs/<moduleName>-Api<api>-desktop.jar`: desktop Minecraft jar remapped
  from MCP/deobf names back to notch names.
- `build/libs/<moduleName>-Api<api>-cloudmc.jar`: CloudMC jar validated against
  the latest public stubs and enriched with GraalVM native-image metadata.

Native-image metadata:

- `generateCloudMcNativeMetadata` runs automatically for the CloudMC jar.
- The plugin detects concrete `ScriptAction`, `VariableProvider` and
  `ScriptedIterator` implementations by type hierarchy, not by class name.
- The CloudMC jar receives:
  - `META-INF/cloudmc/cloudscript-module.classes`
  - `META-INF/cloudmc/cloudscript-api<api>.classes`
  - `META-INF/native-image/cloudmc/<module>-api<api>/reflect-config.json`
  - `resource-config.json` for non-class module resources, when present.
- Desktop Minecraft and non-native CloudMC still load modules dynamically. A
  GraalVM native Minicraft build can only use modules known at native-image
  build time.

Extra metadata for dynamic code can be declared explicitly:

```kotlin
cloudScriptModule {
    nativeReflectClasses.add("com.example.internal.CreatedByReflection")
    nativeResourcePatterns.add("\\Qassets/example/config.json\\E")
}
```

Deploying to CloudScript:

```powershell
$env:CLOUDSCRIPT_TOKEN = "<session-token>"
.\gradlew.bat deployCloudScriptModule
```

```kotlin
cloudScriptModule {
    apiVersion.set(18)
    moduleName.set("hello-world")
    deployBaseUrl.set("https://cloudscript.bezouro.com.br")
    deployDesktop.set(true)
    deployCloudMc.set(true)
}
```

`deployCloudScriptModule` builds only the enabled variants, validates them, then
uploads them to `POST /api/modules/upload`. When `deployCloudMc` is enabled, the
uploaded CloudMC artifact is the final
`build/libs/<project>-Api<api>-cloudmc.jar` produced by
`generateCloudMcNativeMetadata`, not the intermediate output from
`remapCloudMcModule`.

To request a Microcraft variant from an API 18 desktop upload, enable the
conversion explicitly:

```kotlin
cloudScriptModule {
    apiVersion.set(18)
    convertDesktopToMicrocraft.set(true)
    // Enable only when the converted variant should replace an existing native one.
    replaceNativeMicrocraft.set(false)
}
```

Both options default to `false`. Conversion requires `deployDesktop=true` and
API 18. `replaceNativeMicrocraft=true` additionally requires conversion. The
CloudMC upload and native multi-runtime builds do not request this conversion.

For API 10 and API 18, the normal public build and deploy tasks remain
compatible with existing desktop Minecraft and JVM CloudMC module workflows. The
CloudMC jar now contains extra `META-INF/cloudmc` and
`META-INF/native-image` entries, so consumers that compare byte-for-byte jars,
checksums or signatures should regenerate those values from the final
`build/libs` artifact. Treat `remapCloudMcModule` output as an internal
intermediate; external automation should consume `buildCloudMcModule`,
`buildCloudScriptModule`, `deployCloudScriptModule` or the final jar in
`build/libs`.

The deploy token can be configured with `cloudScriptModule.deployToken`,
`-PcloudScriptToken=...`, or the `CLOUDSCRIPT_TOKEN` environment variable.

Workspace setup can also prepare the developer classpath:

```kotlin
cloudScriptModule {
    apiVersion.set(18)
    setupWorkspace.set(true)
    useWorkspaceClasspath.set(true)
}
```

`setupCloudScriptWorkspace` downloads the official Minecraft client jar from
Mojang metadata and remaps it with bundled SRG mappings using ASM:

- API 10: official `1.5.2` notch jar -> MCP `net/minecraft/src/*` dev jar.
- API 18: official `1.8` notch jar -> MCP/deobf modern package dev jar.

It does not require ObfKit at runtime.

No GitHub token is required for normal consumers. Published releases are served
from public Maven repositories:

```kotlin
repositories {
    maven("https://bezouro.github.io/CloudScriptJava/maven")
    maven("https://bezouro.github.io/Minicraft/maven")
}
```

Tasks:

```text
setupCloudScriptWorkspace
obfuscateDesktopModule
buildDesktopModule
validateCloudScriptModule
remapCloudMcModule
generateCloudMcNativeMetadata
validateCloudMcModule
buildCloudMcModule
buildCloudScriptModule
deployCloudScriptModule
```
