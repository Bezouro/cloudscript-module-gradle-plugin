package br.com.cloudmc.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@DisableCachingByDefault(because = "Downloads external artifacts into a workspace cache.")
public abstract class SetupCloudScriptWorkspaceTask extends DefaultTask {
    private static final String VERSION_MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

    @Input
    public abstract Property<Integer> getApiVersion();

    @Input
    public abstract Property<String> getMinecraftVersion();

    @Input
    public abstract Property<Boolean> getModernMinecraftNames();

    @Input
    @Optional
    public abstract Property<String> getLiteLoaderUrl();

    @Input
    @Optional
    public abstract Property<String> getMacroKeybindUrl();

    @InputFile
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getLiteLoaderJar();

    @InputFile
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getMacroKeybindJar();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @OutputFile
    public abstract RegularFileProperty getMinecraftDeobfJar();

    @OutputFile
    public abstract RegularFileProperty getLiteLoaderOutputJar();

    @OutputFile
    public abstract RegularFileProperty getMacroKeybindOutputJar();

    @Internal
    public File getMinecraftDeobfJarFile() {
        return getOutputDirectory().file("minecraft-" + getMinecraftVersion().get() + "-deobf.jar").get().getAsFile();
    }

    public SetupCloudScriptWorkspaceTask() {
        getMinecraftDeobfJar().convention(getOutputDirectory().file(getMinecraftVersion().map(v -> "minecraft-" + v + "-deobf.jar")));
        getLiteLoaderOutputJar().convention(getOutputDirectory().file("liteloader.jar"));
        getMacroKeybindOutputJar().convention(getOutputDirectory().file("macro-keybind.jar"));
    }

    @TaskAction
    public void run() throws IOException, InterruptedException {
        File outputDir = getOutputDirectory().get().getAsFile();
        outputDir.mkdirs();

        String version = getMinecraftVersion().get();
        File officialJar = new File(outputDir, "minecraft-" + version + "-official.jar");
        if (!officialJar.isFile()) {
            String clientUrl = findClientJarUrl(version);
            download(clientUrl, officialJar);
        }

        MappingSet mappings = loadMappings(version);
        File minecraftOutput = getMinecraftDeobfJar().get().getAsFile();
        if (getApiVersion().get() == 10 && getModernMinecraftNames().get()) {
            File mcpJar = new File(outputDir, "minecraft-" + version + "-mcp.jar");
            remapJar(officialJar, mcpJar, mappings);
            remapJar(mcpJar, minecraftOutput, loadBridgeMappings());
        } else {
            remapJar(officialJar, minecraftOutput, mappings);
        }
        prepareOptionalJar(getLiteLoaderJar(), getLiteLoaderUrl(), getLiteLoaderOutputJar().get().getAsFile());
        prepareOptionalJar(getMacroKeybindJar(), getMacroKeybindUrl(), getMacroKeybindOutputJar().get().getAsFile());

        getLogger().lifecycle("[CloudScript] Workspace ready in {}", outputDir);
    }

    private void prepareOptionalJar(
        RegularFileProperty localJar,
        Property<String> url,
        File output
    ) throws IOException, InterruptedException {
        if (!localJar.isPresent() && (!url.isPresent() || url.get().isBlank())) {
            return;
        }
        output.getParentFile().mkdirs();
        if (localJar.isPresent()) {
            Files.copy(localJar.get().getAsFile().toPath(), output.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return;
        }
        if (url.isPresent() && !url.get().isBlank()) {
            download(url.get(), output);
            return;
        }
    }

    private String findClientJarUrl(String version) throws IOException, InterruptedException {
        String manifest = get(VERSION_MANIFEST);
        String versionUrl = findVersionUrl(manifest, version);
        String versionJson = get(versionUrl);
        Matcher matcher = Pattern
            .compile("\"client\"\\s*:\\s*\\{[^}]*\"url\"\\s*:\\s*\"([^\"]+)\"", Pattern.DOTALL)
            .matcher(versionJson);
        if (!matcher.find()) {
            throw new IOException("Could not find client download URL for Minecraft " + version);
        }
        return matcher.group(1);
    }

    private String findVersionUrl(String manifest, String version) throws IOException {
        Pattern pattern = Pattern.compile(
            "\\{\\s*\"id\"\\s*:\\s*\"" + Pattern.quote(version) + "\".*?\"url\"\\s*:\\s*\"([^\"]+)\"",
            Pattern.DOTALL
        );
        Matcher matcher = pattern.matcher(manifest);
        if (!matcher.find()) {
            throw new IOException("Could not find Minecraft version " + version + " in Mojang manifest");
        }
        return matcher.group(1);
    }

    private String get(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IOException("GET " + url + " failed with HTTP " + response.statusCode());
        }
        return response.body();
    }

    private void download(String url, File output) throws IOException, InterruptedException {
        output.getParentFile().mkdirs();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        HttpResponse<byte[]> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() / 100 != 2) {
            throw new IOException("GET " + url + " failed with HTTP " + response.statusCode());
        }
        Files.write(output.toPath(), response.body());
        getLogger().lifecycle("[CloudScript] Downloaded {}", output);
    }

    private MappingSet loadMappings(String version) throws IOException {
        String resource;
        if ("1.5.2".equals(version)) {
            resource = "cloudmc/minecraft-1.5.2-notch-mcp.srg";
        } else if ("1.8".equals(version)) {
            resource = "cloudmc/minecraft-1.8-notch-mcp.srg";
        } else {
            throw new IOException("Unsupported workspace Minecraft version " + version + "; expected 1.5.2 or 1.8");
        }

        InputStream stream = getClass().getClassLoader().getResourceAsStream(resource);
        if (stream == null) throw new IOException("Missing bundled mapping resource " + resource);

        MappingSet mappings = new MappingSet();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("CL: ")) {
                    String[] parts = line.split("\\s+");
                    mappings.classes.put(parts[1], parts[2]);
                } else if (line.startsWith("FD: ")) {
                    String[] parts = line.split("\\s+");
                    Member source = splitMember(parts[1], null);
                    Member target = splitMember(parts[2], null);
                    mappings.fields.put(source.owner + "/" + source.name, target.name);
                } else if (line.startsWith("MD: ")) {
                    String[] parts = line.split("\\s+");
                    Member source = splitMember(parts[1], parts[2]);
                    Member target = splitMember(parts[3], parts[4]);
                    mappings.methods.put(source, target.name);
                }
            }
        }
        return mappings;
    }

    private MappingSet loadBridgeMappings() throws IOException {
        InputStream stream = getClass().getClassLoader().getResourceAsStream("cloudmc/cloudmc-bridge-1.5.srg");
        if (stream == null) throw new IOException("Missing bundled mapping resource cloudmc/cloudmc-bridge-1.5.srg");

        MappingSet mappings = new MappingSet();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("CL: ")) {
                    String[] parts = line.split("\\s+");
                    if (parts.length >= 3) mappings.classes.put(parts[1], parts[2]);
                }
            }
        }
        return mappings;
    }

    private Member splitMember(String ownerAndName, String descriptor) {
        int separator = ownerAndName.lastIndexOf('/');
        return new Member(ownerAndName.substring(0, separator), ownerAndName.substring(separator + 1), descriptor);
    }

    private void remapJar(File input, File output, MappingSet mappings) throws IOException {
        output.getParentFile().mkdirs();
        int changed = 0;
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(input.toPath()));
             ZipOutputStream result = new ZipOutputStream(Files.newOutputStream(output.toPath()))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                byte[] contents = entry.isDirectory() ? new byte[0] : zip.readAllBytes();
                if (name.endsWith(".class")) {
                    RemapStats stats = new RemapStats();
                    contents = remapClass(contents, mappings, stats);
                    if (stats.changed) changed++;
                    name = mapEntryName(name, mappings);
                }
                ZipEntry outEntry = new ZipEntry(name);
                result.putNextEntry(outEntry);
                if (!entry.isDirectory()) result.write(contents);
                result.closeEntry();
            }
        }
        getLogger().lifecycle("[CloudScript] Remapped Minecraft {} jar to {} ({} changed classes)",
            getMinecraftVersion().get(), output, changed);
    }

    private byte[] remapClass(byte[] bytes, MappingSet mappings, RemapStats stats) {
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassRemapper(writer, new SrgRemapper(mappings, stats)), 0);
        return writer.toByteArray();
    }

    private String mapEntryName(String name, MappingSet mappings) {
        String className = name.substring(0, name.length() - ".class".length());
        String mapped = mappings.classes.get(className);
        if (mapped == null) {
            int inner = className.indexOf('$');
            if (inner > 0) {
                String mappedOuter = mappings.classes.get(className.substring(0, inner));
                if (mappedOuter != null) mapped = mappedOuter + className.substring(inner);
            }
        }
        return (mapped == null ? className : mapped) + ".class";
    }

    private static final class SrgRemapper extends Remapper {
        private final MappingSet mappings;
        private final RemapStats stats;

        private SrgRemapper(MappingSet mappings, RemapStats stats) {
            this.mappings = mappings;
            this.stats = stats;
        }

        @Override
        public String map(String internalName) {
            String mapped = mappings.classes.get(internalName);
            if (mapped != null) {
                stats.changed = true;
                return mapped;
            }
            return internalName;
        }

        @Override
        public String mapFieldName(String owner, String name, String descriptor) {
            String mapped = mappings.fields.get(owner + "/" + name);
            if (mapped != null) {
                stats.changed = true;
                return mapped;
            }
            return name;
        }

        @Override
        public String mapMethodName(String owner, String name, String descriptor) {
            String mapped = mappings.methods.get(new Member(owner, name, descriptor));
            if (mapped == null) {
                String sourceDescriptor = unmapDescriptor(descriptor);
                mapped = mappings.methods.get(new Member(owner, name, sourceDescriptor));
            }
            if (mapped != null) {
                stats.changed = true;
                return mapped;
            }
            return name;
        }

        private String unmapDescriptor(String descriptor) {
            Map<String, String> reverse = new HashMap<>();
            mappings.classes.forEach((source, target) -> reverse.put(target, source));
            Type type = Type.getType(descriptor);
            if (type.getSort() == Type.METHOD) {
                Type[] args = Type.getArgumentTypes(descriptor);
                for (int i = 0; i < args.length; i++) args[i] = unmapType(args[i], reverse);
                return Type.getMethodDescriptor(unmapType(Type.getReturnType(descriptor), reverse), args);
            }
            return unmapType(type, reverse).getDescriptor();
        }

        private Type unmapType(Type type, Map<String, String> reverse) {
            if (type.getSort() == Type.ARRAY) {
                return Type.getType("[".repeat(type.getDimensions()) + unmapType(type.getElementType(), reverse).getDescriptor());
            }
            if (type.getSort() == Type.OBJECT) {
                return Type.getObjectType(reverse.getOrDefault(type.getInternalName(), type.getInternalName()));
            }
            return type;
        }
    }

    private static final class MappingSet {
        final Map<String, String> classes = new HashMap<>();
        final Map<String, String> fields = new HashMap<>();
        final Map<Member, String> methods = new HashMap<>();
    }

    private record Member(String owner, String name, String descriptor) {
    }

    private static final class RemapStats {
        boolean changed;
    }
}
