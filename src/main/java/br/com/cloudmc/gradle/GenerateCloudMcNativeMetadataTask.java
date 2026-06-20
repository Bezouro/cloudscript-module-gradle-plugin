package br.com.cloudmc.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@DisableCachingByDefault(because = "The task rewrites a jar and is fast compared to compilation.")
public abstract class GenerateCloudMcNativeMetadataTask extends DefaultTask {
    private static final Set<String> REGISTERABLE_TYPES = Set.of(
        "net/eq2online/macros/scripting/api/IScriptAction",
        "net/eq2online/macros/scripting/api/IVariableProvider",
        "net/eq2online/macros/scripting/api/IScriptedIterator",
        "com/bezouro/modules/cloudscript/core/implementation/CloudScriptAction",
        "com/bezouro/modules/cloudscript/core/implementation/CloudScriptVariableProvider"
    );

    @Input
    public abstract Property<Integer> getApiVersion();

    @Input
    public abstract Property<String> getModuleName();

    @Input
    public abstract Property<Boolean> getNativeMetadataEnabled();

    @Input
    public abstract Property<Boolean> getIncludeModuleResources();

    @Input
    public abstract ListProperty<String> getAdditionalReflectClasses();

    @Input
    public abstract ListProperty<String> getAdditionalResourcePatterns();

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getInputJar();

    @OutputFile
    public abstract RegularFileProperty getOutputJar();

    @TaskAction
    public void run() throws IOException {
        byte[] input = Files.readAllBytes(getInputJar().get().getAsFile().toPath());
        byte[] output = getNativeMetadataEnabled().get()
            ? enrichJar(input)
            : input;

        java.io.File out = getOutputJar().get().getAsFile();
        if (out.getParentFile() != null) out.getParentFile().mkdirs();
        Files.write(out.toPath(), output);
        getLogger().lifecycle("[CloudScript] Wrote CloudMC native-ready module jar: {}", out);
    }

    private byte[] enrichJar(byte[] jar) throws IOException {
        JarInspection inspection = inspectJar(jar);
        String moduleId = sanitize(getModuleName().get());
        int api = getApiVersion().get();
        List<GeneratedEntry> generatedEntries = new ArrayList<>();

        generatedEntries.add(textEntry(
            "META-INF/cloudmc/cloudscript-module.classes",
            classIndex(inspection.loadableClasses)
        ));
        generatedEntries.add(textEntry(
            "META-INF/cloudmc/cloudscript-api" + api + ".classes",
            classIndex(inspection.loadableClasses)
        ));
        generatedEntries.add(textEntry(
            "META-INF/cloudmc/cloudscript-module.properties",
            "module=" + getModuleName().get() + "\napi=" + api + "\n"
        ));
        generatedEntries.add(textEntry(
            "META-INF/native-image/cloudmc/" + moduleId + "-api" + api + "/reflect-config.json",
            reflectConfig(inspection.reflectClasses)
        ));

        String resourceConfig = resourceConfig(inspection.resourcePaths);
        if (!resourceConfig.isBlank()) {
            generatedEntries.add(textEntry(
                "META-INF/native-image/cloudmc/" + moduleId + "-api" + api + "/resource-config.json",
                resourceConfig
            ));
        }

        return writeJar(jar, generatedEntries);
    }

    private JarInspection inspectJar(byte[] jar) throws IOException {
        Map<String, ClassInfo> classes = new HashMap<>();
        Set<String> resourcePaths = new TreeSet<>();

        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(jar))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                byte[] contents = zip.readAllBytes();
                if (name.endsWith(".class")) {
                    ClassInfo info = readClass(contents);
                    classes.put(info.name, info);
                } else if (getIncludeModuleResources().get() && isModuleResource(name)) {
                    resourcePaths.add(name);
                }
            }
        }

        Set<String> loadable = new TreeSet<>();
        Set<String> reflect = new TreeSet<>();
        for (ClassInfo info : classes.values()) {
            if (isConcrete(info.access) && isRegisterable(info.name, classes)) {
                String className = info.name.replace('/', '.');
                reflect.add(className);
                if (isInstantiable(info)) {
                    loadable.add(className);
                }
            }
        }
        return new JarInspection(loadable, reflect, resourcePaths);
    }

    private ClassInfo readClass(byte[] bytes) {
        ClassNode node = new ClassNode(Opcodes.ASM9);
        new ClassReader(bytes).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return new ClassInfo(
            node.name,
            node.superName,
            node.interfaces == null ? List.of() : new ArrayList<>(node.interfaces),
            node.access,
            hasPublicNoArgsConstructor(node)
        );
    }

    private boolean isInstantiable(ClassInfo info) {
        return isConcrete(info.access) && info.hasPublicNoArgsConstructor();
    }

    private boolean isConcrete(int access) {
        return (access & (Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT | Opcodes.ACC_ANNOTATION | Opcodes.ACC_ENUM)) == 0;
    }

    private boolean hasPublicNoArgsConstructor(ClassNode node) {
        if (node.methods == null) {
            return false;
        }

        for (Object methodObject : node.methods) {
            MethodNode method = (MethodNode) methodObject;
            if ("<init>".equals(method.name)
                && "()V".equals(method.desc)
                && (method.access & Opcodes.ACC_PUBLIC) != 0) {
                return true;
            }
        }
        return false;
    }

    private boolean isRegisterable(String className, Map<String, ClassInfo> classes) {
        ArrayDeque<String> pending = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        pending.add(className);

        while (!pending.isEmpty()) {
            String current = pending.removeFirst();
            if (!visited.add(current)) continue;
            if (REGISTERABLE_TYPES.contains(current)) return true;

            ClassInfo info = classes.get(current);
            if (info == null) continue;
            if (info.superName != null) pending.add(info.superName);
            pending.addAll(info.interfaces);
        }

        String simpleName = className.substring(className.lastIndexOf('/') + 1);
        return simpleName.startsWith("ScriptAction") ||
            simpleName.startsWith("VariableProvider") ||
            simpleName.startsWith("ScriptedIterator");
    }

    private boolean isModuleResource(String name) {
        if (name.equals("META-INF/MANIFEST.MF")) return false;
        if (name.startsWith("META-INF/cloudmc/")) return false;
        if (name.startsWith("META-INF/native-image/")) return false;
        if (name.startsWith("META-INF/") &&
            (name.endsWith(".RSA") || name.endsWith(".SF") || name.endsWith(".DSA"))) {
            return false;
        }
        return true;
    }

    private String classIndex(Set<String> classes) {
        return String.join("\n", classes) + (classes.isEmpty() ? "" : "\n");
    }

    private String reflectConfig(Set<String> registerableClasses) {
        LinkedHashSet<String> classes = new LinkedHashSet<>(registerableClasses);
        classes.addAll(getAdditionalReflectClasses().get());

        StringBuilder json = new StringBuilder();
        json.append("[\n");
        int index = 0;
        for (String className : classes) {
            if (index++ > 0) json.append(",\n");
            json.append("  {\n")
                .append("    \"name\": \"").append(jsonEscape(className)).append("\",\n")
                .append("    \"allDeclaredConstructors\": true,\n")
                .append("    \"allPublicConstructors\": true\n")
                .append("  }");
        }
        json.append("\n]\n");
        return json.toString();
    }

    private String resourceConfig(Set<String> resourcePaths) {
        LinkedHashSet<String> patterns = new LinkedHashSet<>();
        for (String path : resourcePaths) {
            patterns.add("\\Q" + path + "\\E");
        }
        patterns.addAll(getAdditionalResourcePatterns().get());

        if (patterns.isEmpty()) return "";

        StringBuilder json = new StringBuilder();
        json.append("{\n  \"resources\": {\n    \"includes\": [\n");
        int index = 0;
        for (String pattern : patterns) {
            if (index++ > 0) json.append(",\n");
            json.append("      { \"pattern\": \"").append(jsonEscape(pattern)).append("\" }");
        }
        json.append("\n    ]\n  }\n}\n");
        return json.toString();
    }

    private byte[] writeJar(byte[] jar, List<GeneratedEntry> generatedEntries) throws IOException {
        Set<String> generatedNames = new HashSet<>();
        generatedEntries.forEach(entry -> generatedNames.add(entry.name));
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(jar));
             ZipOutputStream result = new ZipOutputStream(out)) {
            Set<String> written = new HashSet<>();
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                byte[] contents = entry.isDirectory() ? new byte[0] : zip.readAllBytes();
                if (generatedNames.contains(name) || !written.add(name)) {
                    continue;
                }

                ZipEntry outputEntry = new ZipEntry(name);
                if (entry.getTime() >= 0) outputEntry.setTime(entry.getTime());
                result.putNextEntry(outputEntry);
                if (!entry.isDirectory()) result.write(contents);
                result.closeEntry();
            }

            generatedEntries.stream()
                .sorted(Comparator.comparing(generatedEntry -> generatedEntry.name))
                .forEach(generatedEntry -> {
                    try {
                        ZipEntry outputEntry = new ZipEntry(generatedEntry.name);
                        outputEntry.setTime(0L);
                        result.putNextEntry(outputEntry);
                        result.write(generatedEntry.contents);
                        result.closeEntry();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException io) throw io;
            throw e;
        }

        return out.toByteArray();
    }

    private GeneratedEntry textEntry(String name, String contents) {
        return new GeneratedEntry(name, contents.getBytes(StandardCharsets.UTF_8));
    }

    private String sanitize(String value) {
        String sanitized = value.replaceAll("[^A-Za-z0-9_.-]+", "-");
        return sanitized.isBlank() ? "module" : sanitized;
    }

    private String jsonEscape(String value) {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\b", "\\b")
            .replace("\f", "\\f")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }

    private record ClassInfo(String name, String superName, List<String> interfaces, int access, boolean hasPublicNoArgsConstructor) {
    }

    private record JarInspection(Set<String> loadableClasses, Set<String> reflectClasses, Set<String> resourcePaths) {
    }

    private record GeneratedEntry(String name, byte[] contents) {
    }
}
