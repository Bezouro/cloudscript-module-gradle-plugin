# PressButton multitarget module example

This is a real `pressbutton(<0..2>)` CloudScript module example for:

- Desktop Minecraft 1.5
- Desktop Minecraft 1.8
- CloudMC / Minicraft 1.5
- CloudMC / Minicraft 1.8
- Microcraft

The common action parses and validates the CloudScript argument. Runtime-specific
code decides how to press the enchantment button.

```text
src/common/java
  CloudScriptActionPressButton.java
  PressButtonService.java
  PressButtonRuntime.java

src/desktop15/java
src/desktop18/java
src/minicraft15/java
src/minicraft18/java
src/microcraft/java
```

Desktop and Minicraft targets call the real Minecraft client API:

```java
Minecraft.getMinecraft().playerController.sendEnchantPacket(windowId, enchantment);
```

Microcraft calls the exposed runtime contract:

```java
MicrocraftRuntime.enchantItem(enchantment);
```

The Gradle plugin builds this layout with `architecture = "multi-runtime"`:

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

Run:

```powershell
..\..\gradlew.bat -p . buildCloudScriptModule
```

The plugin imports the matching stubs for each runtime, obfuscates only
`desktop15` and `desktop18`, and leaves the Minicraft/Microcraft jars as-is.
