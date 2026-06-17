# CloudScript Module Gradle Plugin

Build helper for CloudScript modules that target Desktop Minecraft and CloudMC.

Current behavior:

- API 10 / Minecraft 1.5: compiles normally, remaps `net/minecraft/src/*` class
  references to CloudMC package names using the embedded 1.5 bridge SRG, then
  validates against the latest CloudMC API 10 stubs. It also remaps the desktop
  jar back to notch names using the embedded Minecraft 1.5.2 SRG.
- API 18 / Minecraft 1.8: no naming remap is applied, but the output jar is
  validated against the latest CloudMC API 18 stubs. It also remaps the desktop
  jar back to notch names using the embedded Minecraft 1.8 SRG.

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
    id("br.com.cloudmc.cloudscript-module") version "0.1.0"
}

cloudScriptModule {
    apiVersion.set(18)
    setupWorkspace.set(true)
    useWorkspaceClasspath.set(true)
}
```

The plugin downloads/remaps the Minecraft dev jar, adds CloudScript/Macro
Keybind/LiteLoader dev stubs and CloudMC stubs as `compileOnly`, then produces:

- `build/libs/<project>-Api<api>-desktop.jar`: desktop Minecraft jar remapped
  from MCP/deobf names back to notch names.
- `build/libs/<project>-Api<api>-cloudmc.jar`: CloudMC jar validated against
  the latest public stubs.

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
remapCloudMcModule
validateCloudMcModule
buildCloudMcModule
buildCloudScriptModule
```
