package br.com.cloudmc.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@DisableCachingByDefault(because = "Validation has no output artifact and reports remap failures directly.")
public abstract class ValidateDesktopModuleTask extends DefaultTask {
    @Input
    public abstract Property<Integer> getApiVersion();

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getModuleJar();

    @Classpath
    public abstract ConfigurableFileCollection getRemapClasspath();

    @TaskAction
    public void run() throws IOException {
        int api = getApiVersion().get();
        if (api != 10) {
            getLogger().lifecycle("[CloudScript] Desktop API {} remap validation skipped; strict desktop validation currently targets API 10.", api);
            return;
        }

        MappingSet mappings = loadMappings();
        applyModernApi10Bridge(mappings);
        ClassHierarchy hierarchy = ClassHierarchy.from(getRemapClasspath(), mappings);
        Map<String, Set<String>> issues = inspectJar(
            Files.readAllBytes(getModuleJar().get().getAsFile().toPath()),
            mappings,
            hierarchy
        );

        if (!issues.isEmpty()) {
            StringBuilder message = new StringBuilder();
            message.append("Desktop API 10 module is not fully obfuscated; deobfuscated Minecraft symbols remain:");
            issues.entrySet().stream().limit(40).forEach(entry -> {
                message.append(System.lineSeparator()).append(" - ").append(entry.getKey());
                entry.getValue().stream().limit(8).forEach(symbol ->
                    message.append(System.lineSeparator()).append("   * ").append(symbol)
                );
                if (entry.getValue().size() > 8) {
                    message.append(System.lineSeparator()).append("   * ... ").append(entry.getValue().size() - 8).append(" more");
                }
            });
            if (issues.size() > 40) {
                message.append(System.lineSeparator()).append(" - ... ").append(issues.size() - 40).append(" more classes");
            }
            throw new IllegalStateException(message.toString());
        }

        getLogger().lifecycle("[CloudScript] Desktop API 10 obfuscation validation passed for {}", getModuleJar().get().getAsFile());
    }

    private MappingSet loadMappings() throws IOException {
        InputStream stream = getClass().getClassLoader().getResourceAsStream("cloudmc/minecraft-1.5.2-notch-mcp.srg");
        if (stream == null) throw new IOException("Missing bundled mapping resource cloudmc/minecraft-1.5.2-notch-mcp.srg");

        MappingSet mappings = new MappingSet();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\\s+");
                if (line.startsWith("CL: ") && parts.length >= 3) {
                    mappings.classes.put(parts[2], parts[1]);
                } else if (line.startsWith("FD: ") && parts.length >= 3) {
                    Member notch = splitMember(parts[1], null);
                    Member mcp = splitMember(parts[2], null);
                    mappings.fields.put(mcp.owner + "/" + mcp.name, notch.name);
                } else if (line.startsWith("MD: ") && parts.length >= 5) {
                    Member notch = splitMember(parts[1], parts[2]);
                    Member mcp = splitMember(parts[3], parts[4]);
                    mappings.methods.put(mcp, notch.name);
                }
            }
        }
        return mappings;
    }

    private void applyModernApi10Bridge(MappingSet mappings) throws IOException {
        InputStream stream = getClass().getClassLoader().getResourceAsStream("cloudmc/cloudmc-bridge-1.5.srg");
        if (stream == null) throw new IOException("Missing bundled mapping resource cloudmc/cloudmc-bridge-1.5.srg");

        Map<String, String> bridge = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\\s+");
                if (line.startsWith("CL: ") && parts.length >= 3) {
                    bridge.put(parts[1], parts[2]);
                }
            }
        }

        Map<String, String> classesToAdd = new HashMap<>();
        Map<String, String> fieldsToAdd = new HashMap<>();
        Map<Member, String> methodsToAdd = new HashMap<>();
        bridge.forEach((mcpOwner, modernOwner) -> {
            String notchOwner = mappings.classes.get(mcpOwner);
            if (notchOwner != null) classesToAdd.put(modernOwner, notchOwner);

            mappings.fields.forEach((key, value) -> {
                String prefix = mcpOwner + "/";
                if (key.startsWith(prefix)) fieldsToAdd.put(modernOwner + "/" + key.substring(prefix.length()), value);
            });

            mappings.methods.forEach((member, value) -> {
                if (member.owner.equals(mcpOwner)) {
                    methodsToAdd.put(new Member(modernOwner, member.name, bridgeDescriptor(member.descriptor, bridge)), value);
                    methodsToAdd.put(new Member(modernOwner, member.name, mapDescriptor(member.descriptor, mappings.classes)), value);
                }
            });
        });

        mappings.methods.forEach((member, value) -> {
            methodsToAdd.put(new Member(member.owner, member.name, bridgeDescriptor(member.descriptor, bridge)), value);
            methodsToAdd.put(new Member(member.owner, member.name, mapDescriptor(member.descriptor, mappings.classes)), value);
        });

        mappings.classes.putAll(classesToAdd);
        mappings.fields.putAll(fieldsToAdd);
        mappings.methods.putAll(methodsToAdd);
    }

    private Map<String, Set<String>> inspectJar(byte[] jar, MappingSet mappings, ClassHierarchy hierarchy) throws IOException {
        Map<String, Set<String>> issues = new TreeMap<>();
        Set<String> forbiddenClasses = loadForbiddenClasses(mappings);
        List<String> sortedForbidden = new ArrayList<>(forbiddenClasses);
        sortedForbidden.sort((left, right) -> Integer.compare(right.length(), left.length()));

        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(jar))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory() || !entry.getName().endsWith(".class")) continue;

                String classText = new String(zip.readAllBytes(), StandardCharsets.ISO_8859_1);
                Set<String> classIssues = new TreeSet<>();
                for (String forbidden : sortedForbidden) {
                    if (classText.contains(forbidden)) {
                        classIssues.add("class " + forbidden);
                    }
                }
                inspectMemberInstructions(classText.getBytes(StandardCharsets.ISO_8859_1), mappings, hierarchy, classIssues);
                if (!classIssues.isEmpty()) {
                    issues.put(entry.getName(), classIssues);
                }
            }
        }

        return issues;
    }

    private Set<String> loadForbiddenClasses(MappingSet mappings) {
        Set<String> forbidden = new HashSet<>();
        mappings.classes.forEach((source, target) -> {
            if (!source.equals(target)) forbidden.add(source);
        });
        return forbidden;
    }

    private void inspectMemberInstructions(byte[] bytes, MappingSet mappings, ClassHierarchy hierarchy, Set<String> issues) {
        ClassReader reader = new ClassReader(bytes);
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                String mapped = mappings.fields.get(reader.getClassName() + "/" + name);
                if (mapped != null && !mapped.equals(name)) {
                    issues.add("field declaration " + reader.getClassName() + "/" + name + " should be " + mapped);
                }
                return super.visitField(access, name, descriptor, signature, value);
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                        String mapped = resolveField(owner, name, mappings, hierarchy);
                        if (mapped != null && !mapped.equals(name)) {
                            issues.add("field ref " + owner + "/" + name + " should be " + mapped);
                        }
                    }

                    @Override
                    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                        String mapped = resolveMethod(owner, name, descriptor, mappings, hierarchy);
                        if (mapped != null && !mapped.equals(name)) {
                            issues.add("method ref " + owner + "/" + name + descriptor + " should be " + mapped);
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    }

    private String resolveField(String owner, String name, MappingSet mappings, ClassHierarchy hierarchy) {
        for (String sourceOwner : hierarchy.sourceOwners(owner)) {
            for (String candidate : hierarchy.walk(sourceOwner)) {
                String mapped = mappings.fields.get(candidate + "/" + name);
                if (mapped != null) return mapped;
            }
        }
        return null;
    }

    private String resolveMethod(String owner, String name, String descriptor, MappingSet mappings, ClassHierarchy hierarchy) {
        for (String sourceOwner : hierarchy.sourceOwners(owner)) {
            for (String candidate : hierarchy.walk(sourceOwner)) {
                for (Member member : mappings.methods.keySet()) {
                    if (member.owner.equals(candidate) && member.name.equals(name)) {
                        return mappings.methods.get(member);
                    }
                }
            }
        }
        return null;
    }

    private Member splitMember(String ownerAndName, String descriptor) {
        int separator = ownerAndName.lastIndexOf('/');
        return new Member(ownerAndName.substring(0, separator), ownerAndName.substring(separator + 1), descriptor);
    }

    private String bridgeDescriptor(String descriptor, Map<String, String> bridge) {
        return mapDescriptor(descriptor, bridge);
    }

    private String mapDescriptor(String descriptor, Map<String, String> classes) {
        Type type = Type.getType(descriptor);
        if (type.getSort() == Type.METHOD) {
            Type[] args = Type.getArgumentTypes(descriptor);
            for (int i = 0; i < args.length; i++) args[i] = mapType(args[i], classes);
            return Type.getMethodDescriptor(mapType(Type.getReturnType(descriptor), classes), args);
        }
        return mapType(type, classes).getDescriptor();
    }

    private Type mapType(Type type, Map<String, String> classes) {
        if (type.getSort() == Type.ARRAY) {
            return Type.getType("[".repeat(type.getDimensions()) + mapType(type.getElementType(), classes).getDescriptor());
        }
        if (type.getSort() == Type.OBJECT) {
            return Type.getObjectType(classes.getOrDefault(type.getInternalName(), type.getInternalName()));
        }
        return type;
    }

    private static final class MappingSet {
        final Map<String, String> classes = new HashMap<>();
        final Map<String, String> fields = new HashMap<>();
        final Map<Member, String> methods = new HashMap<>();
    }

    private static final class ClassHierarchy {
        private final Map<String, ClassInfo> classes;
        private final Map<String, List<String>> reverseClasses;

        private ClassHierarchy(Map<String, ClassInfo> classes, Map<String, List<String>> reverseClasses) {
            this.classes = classes;
            this.reverseClasses = reverseClasses;
        }

        static ClassHierarchy from(ConfigurableFileCollection classpath, MappingSet mappings) throws IOException {
            Map<String, ClassInfo> classes = new HashMap<>();
            for (java.io.File file : classpath.getFiles()) {
                if (!file.exists()) continue;
                if (file.isDirectory()) {
                    try (java.util.stream.Stream<Path> paths = Files.walk(file.toPath())) {
                        for (Path path : paths.filter(path -> path.toString().endsWith(".class")).toList()) {
                            readClass(classes, Files.readAllBytes(path), mappings);
                        }
                    }
                } else if (file.getName().endsWith(".jar")) {
                    try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(file.toPath()))) {
                        ZipEntry entry;
                        while ((entry = zip.getNextEntry()) != null) {
                            if (!entry.isDirectory() && entry.getName().endsWith(".class")) {
                                readClass(classes, zip.readAllBytes(), mappings);
                            }
                        }
                    }
                }
            }

            Map<String, List<String>> reverseClasses = new HashMap<>();
            mappings.classes.forEach((source, target) ->
                reverseClasses.computeIfAbsent(target, ignored -> new ArrayList<>()).add(source)
            );
            return new ClassHierarchy(classes, reverseClasses);
        }

        private static void readClass(Map<String, ClassInfo> classes, byte[] bytes, MappingSet mappings) {
            ClassReader reader = new ClassReader(bytes);
            String name = reader.getClassName();
            classes.put(name, new ClassInfo(reader.getSuperName(), reader.getInterfaces()));

            String mappedName = mappings.classes.get(name);
            if (mappedName != null) {
                String mappedSuper = reader.getSuperName() == null ? null : mappings.classes.getOrDefault(reader.getSuperName(), reader.getSuperName());
                String[] interfaces = reader.getInterfaces();
                String[] mappedInterfaces = new String[interfaces.length];
                for (int i = 0; i < interfaces.length; i++) {
                    mappedInterfaces[i] = mappings.classes.getOrDefault(interfaces[i], interfaces[i]);
                }
                classes.put(mappedName, new ClassInfo(mappedSuper, mappedInterfaces));
            }
        }

        List<String> sourceOwners(String owner) {
            List<String> sources = reverseClasses.get(owner);
            if (sources == null || sources.isEmpty()) return List.of(owner);
            return sources;
        }

        Iterable<String> walk(String owner) {
            ArrayDeque<String> queue = new ArrayDeque<>();
            Set<String> seen = new HashSet<>();
            queue.add(owner);
            return () -> new java.util.Iterator<>() {
                @Override
                public boolean hasNext() {
                    return !queue.isEmpty();
                }

                @Override
                public String next() {
                    String current = queue.removeFirst();
                    if (seen.add(current)) {
                        ClassInfo info = classes.get(current);
                        if (info != null) {
                            if (info.superName != null) queue.addLast(info.superName);
                            for (String itf : info.interfaces) queue.addLast(itf);
                        }
                    }
                    return current;
                }
            };
        }
    }

    private record ClassInfo(String superName, String[] interfaces) {
    }

    private record Member(String owner, String name, String descriptor) {
    }
}
