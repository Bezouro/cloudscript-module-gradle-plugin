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
public abstract class RemapCloudMcModuleTask extends DefaultTask {
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
        byte[] input = Files.readAllBytes(getInputJar().get().getAsFile().toPath());
        byte[] output;
        if (api == 10) {
            output = remapApi10(input);
        } else if (api == 18) {
            output = input;
        } else {
            throw new IllegalArgumentException("Unsupported CloudScript API " + api + "; expected 10 or 18");
        }

        java.io.File out = getOutputJar().get().getAsFile();
        if (out.getParentFile() != null) out.getParentFile().mkdirs();
        Files.write(out.toPath(), output);
        getLogger().lifecycle("[CloudScript] Wrote CloudMC module jar: {}", out);
    }

    private byte[] remapApi10(byte[] jar) throws IOException {
        Map<String, String> classes = loadBridge();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int changed = 0;

        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(jar));
             ZipOutputStream result = new ZipOutputStream(out)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                byte[] contents = entry.isDirectory() ? new byte[0] : zip.readAllBytes();
                if (name.endsWith(".class")) {
                    RemapStats stats = new RemapStats();
                    contents = remapClass(contents, classes, stats);
                    if (stats.changed) changed++;
                    name = mapEntryName(name, classes);
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

        getLogger().lifecycle("[CloudScript] API 10 class remap changed {} classes", changed);
        return out.toByteArray();
    }

    private byte[] remapClass(byte[] bytes, Map<String, String> classes, RemapStats stats) {
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassRemapper(writer, new BridgeRemapper(classes, stats)), 0);
        return writer.toByteArray();
    }

    private String mapEntryName(String entry, Map<String, String> classes) {
        String className = entry.substring(0, entry.length() - ".class".length());
        String mapped = classes.get(className);
        if (mapped == null) {
            int inner = className.indexOf('$');
            if (inner > 0) {
                String outer = className.substring(0, inner);
                String mappedOuter = classes.get(outer);
                if (mappedOuter != null) mapped = mappedOuter + className.substring(inner);
            }
        }
        return (mapped == null ? className : mapped) + ".class";
    }

    private boolean isSignature(String name) {
        return name.startsWith("META-INF/") &&
            (name.endsWith(".RSA") || name.endsWith(".SF") || name.endsWith(".DSA"));
    }

    private Map<String, String> loadBridge() throws IOException {
        InputStream stream = getClass().getClassLoader().getResourceAsStream("cloudmc/cloudmc-bridge-1.5.srg");
        if (stream == null) throw new IOException("Missing bundled resource cloudmc/cloudmc-bridge-1.5.srg");

        Map<String, String> classes = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("CL: ")) continue;
                String[] parts = line.split("\\s+");
                if (parts.length >= 3) classes.put(parts[1], parts[2]);
            }
        }
        return classes;
    }

    private static final class BridgeRemapper extends Remapper {
        private final Map<String, String> classes;
        private final RemapStats stats;

        private BridgeRemapper(Map<String, String> classes, RemapStats stats) {
            this.classes = classes;
            this.stats = stats;
        }

        @Override
        public String map(String internalName) {
            String mapped = classes.get(internalName);
            if (mapped != null) {
                stats.changed = true;
                return mapped;
            }
            return internalName;
        }
    }

    private static final class RemapStats {
        boolean changed;
    }
}
