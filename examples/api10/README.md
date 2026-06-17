# CloudScript API 10 example

Standalone example module for CloudScript API 10 / Minecraft 1.5.2.

By default this example uses classic MCP 1.5 imports such as
`net.minecraft.src.WorldClient`. To develop API 10 with CloudMC-style modern
packages, set `modernMinecraftNames.set(true)` in `build.gradle.kts` and use
imports such as `net.minecraft.client.multiplayer.WorldClient`.

Build both desktop and CloudMC variants:

```powershell
.\gradlew.bat buildCloudScriptModule
```

Outputs:

```text
build/libs/cloudscript-api10-example-Api10-desktop.jar
build/libs/cloudscript-api10-example-Api10-cloudmc.jar
```

Deploy:

```powershell
$env:CLOUDSCRIPT_TOKEN = "<session-token>"
.\gradlew.bat deployCloudScriptModule
```
