# CloudScript API 10 example

Standalone example module for CloudScript API 10 / Minecraft 1.5.2.

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
