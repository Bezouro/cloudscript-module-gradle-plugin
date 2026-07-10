# CloudScript multitarget module example

This example shows the recommended structure for modules that should run on:

- Desktop Minecraft 1.5 / MacroMod API 10
- Desktop Minecraft 1.8 / MacroMod API 18
- CloudMC / Minicraft
- Microcraft

The important rule is that module behavior lives in `src/common/java`. Runtime
details live behind tiny adapters selected by the build target.

```text
src/common/java
  CloudScriptActionSmartHello.java
  SmartHelloService.java
  RuntimePort.java

src/adapter-desktop15/java
  RuntimePorts.java

src/adapter-desktop18/java
  RuntimePorts.java

src/adapter-minicraft/java
  RuntimePorts.java

src/adapter-microcraft/java
  RuntimePorts.java
```

Each adapter provides the same class:

```java
br.com.cloudmc.examples.multitarget.runtime.RuntimePorts
```

That lets the common action compile unchanged for every runtime.

The current public CloudScript module plugin builds one API version at a time.
Until the plugin grows a first-class `targets = [...]` DSL, use this project as
the shape to copy into real builds:

- API 10 target: compile `src/common/java` + `src/adapter-desktop15/java`
- API 18 target: compile `src/common/java` + `src/adapter-desktop18/java`
- CloudMC/Minicraft target: compile `src/common/java` + `src/adapter-minicraft/java`
- Microcraft target: compile `src/common/java` + `src/adapter-microcraft/java`

The sample uses `CloudScriptMinecraftRuntimePort` to call
`com.bezouro.modules.cloudscript.core.adapter.MinecraftAdapter`. That class has
the same package and class name in every target, but its implementation is
runtime-specific:

- Desktop 1.5 reads player/session/chat/server through the 1.5 Minecraft and
  MacroMod classes.
- Desktop 1.8 reads the same information through the 1.8 Minecraft and MacroMod
  classes.
- CloudMC/Minicraft uses the CloudMC adapter generated for the selected
  Minecraft API target.
- Microcraft uses the Microcraft CloudScript host behind its adapter.

It also demonstrates a capability that is not just delegated to an existing
CloudScript adapter: `RuntimePort.onlinePlayers()` and
`RuntimePort.blockAt(x, y, z)`.

- `Desktop15RuntimePort` reads `Minecraft.getMinecraft().theWorld.playerEntities`
  and extracts names from the 1.5 player entities.
- `Desktop18RuntimePort` reads the 1.8 tab list through
  `Minecraft.getMinecraft().getNetHandler().getPlayerInfoMap()`.
- `MinicraftRuntimePort` uses the CloudMC/MacroMod iterator contract when the
  runtime exposes `actionGetIteratorRows("players")`, with a safe fallback for
  older targets.
- `MicrocraftRuntimePort` calls Microcraft's explicit
  `actionGetIteratorRows("players")`, which is backed by Microcraft's bot state.

For block lookup:

- Common code receives a `BlockView`, not a runtime block object. That keeps
  `net.minecraft.block.Block`, 1.8 `IBlockState`, Microcraft `BlockState` and
  legacy 1.5 block ids out of module behavior code.

- `Desktop15RuntimePort` calls the 1.5 world methods
  `getBlockId(x, y, z)` and `getBlockMetadata(x, y, z)`.
- `Desktop18RuntimePort` calls
  `Minecraft.getMinecraft().theWorld.getBlockState(new BlockPos(x, y, z))`,
  then converts the block through 1.8 block/id/name APIs.
- `MinicraftRuntimePort` uses the CloudMC/MacroMod provider block methods by
  reflection so the example remains tolerant of older API jars.
- `MicrocraftRuntimePort` calls
  `com.bezouro.modules.cloudscript.microcraft.MicrocraftRuntime.blockAt(...)`.
  That runtime class also exposes `MicrocraftRuntime.host()` for modules that
  need direct access to the public `CloudScriptHost` contract.

Use `MicrocraftRuntime.host()` sparingly. Most modules should prefer a small
target adapter method such as `MicrocraftRuntime.blockAt(...)` so the module's
common logic does not grow direct runtime dependencies.

For modules that only use MacroMod provider APIs, the adapter can be as small as
`MacroProviderRuntimePort`. When a module needs runtime features such as world
blocks, container clicks, entity attacks or movement, add methods to
`RuntimePort` and implement them per adapter instead of branching inside the
action.

The Microcraft variant was syntax-checked against the local Microcraft classes
and CloudScript Microcraft module with Java 8 bytecode settings. A production
plugin task should wire those classpaths automatically.

## Target selection

This sample accepts a `target` Gradle property so the selected adapter is the
only one compiled into the jar:

```powershell
.\gradlew.bat jar -Ptarget=desktop15
.\gradlew.bat jar -Ptarget=desktop18
.\gradlew.bat jar -Ptarget=minicraft
.\gradlew.bat jar -Ptarget=microcraft
```

Real module builds still need the matching CloudScript/MacroMod/Microcraft
compile-only stubs. The target-specific plugin DSL should add those
automatically.

## Example macro

After loading the module:

```text
$${
smarthello("server")
}$$
```

The common code decides what to do. The selected adapter decides how to talk to
the current runtime.
