# CloudScript API 18 example

Standalone example module for CloudScript API 18 / Minecraft 1.8.

Build both desktop and CloudMC variants:

```powershell
.\gradlew.bat buildCloudScriptModule
```

Outputs:

```text
build/libs/cloudscript-api18-example-Api18-desktop.jar
build/libs/cloudscript-api18-example-Api18-cloudmc.jar
```

Deploy:

```powershell
$env:CLOUDSCRIPT_TOKEN = "<session-token>"
.\gradlew.bat deployCloudScriptModule
```
