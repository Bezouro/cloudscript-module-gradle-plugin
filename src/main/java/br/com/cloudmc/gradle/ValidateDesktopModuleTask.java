package br.com.cloudmc.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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

    @TaskAction
    public void run() throws IOException {
        int api = getApiVersion().get();
        if (api != 10) {
            getLogger().lifecycle("[CloudScript] Desktop API {} remap validation skipped; strict desktop validation currently targets API 10.", api);
            return;
        }

        Set<String> forbiddenClasses = loadForbiddenApi10Classes();
        Map<String, Set<String>> issues = inspectJar(
            Files.readAllBytes(getModuleJar().get().getAsFile().toPath()),
            forbiddenClasses
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

    private Set<String> loadForbiddenApi10Classes() throws IOException {
        Map<String, String> mcpToNotch = loadNotchMcpClasses();
        Map<String, String> mcpToModern = loadBridgeClasses();
        Set<String> forbidden = new HashSet<>();

        mcpToNotch.forEach((mcp, notch) -> {
            if (!mcp.equals(notch)) {
                forbidden.add(mcp);
            }
        });

        mcpToModern.forEach((mcp, modern) -> {
            String notch = mcpToNotch.get(mcp);
            if (notch != null && !modern.equals(notch)) {
                forbidden.add(modern);
            }
        });

        return forbidden;
    }

    private Map<String, String> loadNotchMcpClasses() throws IOException {
        InputStream stream = getClass().getClassLoader().getResourceAsStream("cloudmc/minecraft-1.5.2-notch-mcp.srg");
        if (stream == null) throw new IOException("Missing bundled mapping resource cloudmc/minecraft-1.5.2-notch-mcp.srg");

        Map<String, String> classes = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\\s+");
                if (line.startsWith("CL: ") && parts.length >= 3) {
                    classes.put(parts[2], parts[1]);
                }
            }
        }
        return classes;
    }

    private Map<String, String> loadBridgeClasses() throws IOException {
        InputStream stream = getClass().getClassLoader().getResourceAsStream("cloudmc/cloudmc-bridge-1.5.srg");
        if (stream == null) throw new IOException("Missing bundled mapping resource cloudmc/cloudmc-bridge-1.5.srg");

        Map<String, String> classes = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\\s+");
                if (line.startsWith("CL: ") && parts.length >= 3) {
                    classes.put(parts[1], parts[2]);
                }
            }
        }
        return classes;
    }

    private Map<String, Set<String>> inspectJar(byte[] jar, Set<String> forbiddenClasses) throws IOException {
        Map<String, Set<String>> issues = new TreeMap<>();
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
                        classIssues.add(forbidden);
                    }
                }
                if (!classIssues.isEmpty()) {
                    issues.put(entry.getName(), classIssues);
                }
            }
        }

        return issues;
    }
}
