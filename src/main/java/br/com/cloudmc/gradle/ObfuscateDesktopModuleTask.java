package br.com.cloudmc.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@DisableCachingByDefault(because = "The task is fast and rewrites jar timestamps from input entries.")
public abstract class ObfuscateDesktopModuleTask extends DefaultTask {
    @Input
    public abstract Property<Integer> getApiVersion();

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getInputJar();

    @OutputFile
    public abstract RegularFileProperty getOutputJar();

    @TaskAction
    public void run() throws IOException {
        int api = getApiVersion().get();
        MappingSet mappings = loadMappings(api);

        java.io.File out = getOutputJar().get().getAsFile();
        if (out.getParentFile() != null) out.getParentFile().mkdirs();

        int changed = remapJar(getInputJar().get().getAsFile().toPath(), out.toPath(), mappings);
        getLogger().lifecycle("[CloudScript] Wrote desktop obfuscated module jar: {} ({} changed classes)", out, changed);
    }

    private MappingSet loadMappings(int api) throws IOException {
        String resource;
        if (api == 10) {
            resource = "cloudmc/minecraft-1.5.2-notch-mcp.srg";
        } else if (api == 18) {
            resource = "cloudmc/minecraft-1.8-notch-mcp.srg";
        } else {
            throw new IllegalArgumentException("Unsupported CloudScript API " + api + "; expected 10 or 18");
        }

        InputStream stream = getClass().getClassLoader().getResourceAsStream(resource);
        if (stream == null) throw new IOException("Missing bundled mapping resource " + resource);

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

    private Member splitMember(String ownerAndName, String descriptor) {
        int separator = ownerAndName.lastIndexOf('/');
        return new Member(ownerAndName.substring(0, separator), ownerAndName.substring(separator + 1), descriptor);
    }

    private int remapJar(java.nio.file.Path input, java.nio.file.Path output, MappingSet mappings) throws IOException {
        int changed = 0;
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(input));
             ZipOutputStream result = new ZipOutputStream(Files.newOutputStream(output))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                byte[] contents = entry.isDirectory() ? new byte[0] : zip.readAllBytes();
                if (name.endsWith(".class")) {
                    RemapStats stats = new RemapStats();
                    contents = remapClass(contents, mappings, stats);
                    if (stats.changed) changed++;
                    name = mapEntryName(name, mappings);
                } else if (isSignature(name)) {
                    continue;
                }

                ZipEntry outputEntry = new ZipEntry(name);
                if (entry.getTime() >= 0) outputEntry.setTime(entry.getTime());
                result.putNextEntry(outputEntry);
                if (!entry.isDirectory()) result.write(contents);
                result.closeEntry();
            }
        }
        return changed;
    }

    private byte[] remapClass(byte[] bytes, MappingSet mappings, RemapStats stats) {
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassRemapper(writer, new DesktopRemapper(mappings, stats)), 0);
        return writer.toByteArray();
    }

    private String mapEntryName(String entry, MappingSet mappings) {
        String className = entry.substring(0, entry.length() - ".class".length());
        String mapped = mappings.classes.get(className);
        if (mapped == null) {
            int inner = className.indexOf('$');
            if (inner > 0) {
                String outer = className.substring(0, inner);
                String mappedOuter = mappings.classes.get(outer);
                if (mappedOuter != null) mapped = mappedOuter + className.substring(inner);
            }
        }
        return (mapped == null ? className : mapped) + ".class";
    }

    private boolean isSignature(String name) {
        return name.startsWith("META-INF/") &&
            (name.endsWith(".RSA") || name.endsWith(".SF") || name.endsWith(".DSA"));
    }

    private static final class DesktopRemapper extends Remapper {
        private final MappingSet mappings;
        private final RemapStats stats;
        private final Map<String, String> reverseClasses;

        private DesktopRemapper(MappingSet mappings, RemapStats stats) {
            this.mappings = mappings;
            this.stats = stats;
            this.reverseClasses = new HashMap<>();
            mappings.classes.forEach((source, target) -> reverseClasses.put(target, source));
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
            String sourceDescriptor = unmapDescriptor(descriptor);
            String mapped = mappings.methods.get(new Member(owner, name, sourceDescriptor));
            if (mapped == null) mapped = mappings.methods.get(new Member(owner, name, descriptor));
            if (mapped != null) {
                stats.changed = true;
                return mapped;
            }
            return name;
        }

        private String unmapDescriptor(String descriptor) {
            Type type = Type.getType(descriptor);
            if (type.getSort() == Type.METHOD) {
                Type[] args = Type.getArgumentTypes(descriptor);
                for (int i = 0; i < args.length; i++) args[i] = unmapType(args[i]);
                return Type.getMethodDescriptor(unmapType(Type.getReturnType(descriptor)), args);
            }
            return unmapType(type).getDescriptor();
        }

        private Type unmapType(Type type) {
            if (type.getSort() == Type.ARRAY) {
                return Type.getType("[".repeat(type.getDimensions()) + unmapType(type.getElementType()).getDescriptor());
            }
            if (type.getSort() == Type.OBJECT) {
                return Type.getObjectType(reverseClasses.getOrDefault(type.getInternalName(), type.getInternalName()));
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
