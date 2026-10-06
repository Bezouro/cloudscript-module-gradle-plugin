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
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@DisableCachingByDefault(because = "Validation has no output artifact and reports CloudScript rule failures directly.")
public abstract class ValidateCloudScriptModuleTask extends DefaultTask {
    private static final String API_VERSION_ANNOTATION = "Lnet/eq2online/macros/scripting/api/APIVersion;";

    @Input
    public abstract Property<Integer> getApiVersion();

    /** -1 for headless runtimes, 1 for Desktop, 0 for the legacy shared artifact. */
    @Input
    public abstract Property<Integer> getExpectedApiSign();

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getModuleJar();

    public ValidateCloudScriptModuleTask() {
        getExpectedApiSign().convention(0);
    }

    @TaskAction
    public void run() throws IOException {
        int expectedApi = getApiVersion().get();
        int expectedSign = getExpectedApiSign().get();
        if (expectedApi != 10 && expectedApi != 18 && expectedApi != 26) {
            throw new IllegalStateException("Unsupported CloudScript API " + expectedApi + "; expected 10, 18 or 26");
        }
        if (expectedSign < -1 || expectedSign > 1) {
            throw new IllegalStateException("Unsupported API annotation sign " + expectedSign + "; expected -1, 0 or 1");
        }

        List<String> issues = new ArrayList<>();
        int annotatedClasses = inspectJar(Files.readAllBytes(getModuleJar().get().getAsFile().toPath()), expectedApi, expectedSign, issues);
        if (!issues.isEmpty()) {
            throw new IllegalStateException("CloudScript module rule violations:" + System.lineSeparator() + String.join(System.lineSeparator(), issues));
        }
        if (annotatedClasses == 0) {
            getLogger().warn("[CloudScript] No @APIVersion annotations found in {}. Backend analysis may reject modules without registered actions.", getModuleJar().get().getAsFile());
        }

        getLogger().lifecycle("[CloudScript] CloudScript API {} rules passed for {}", expectedApi, getModuleJar().get().getAsFile());
    }

    private int inspectJar(byte[] jar, int expectedApi, int expectedSign, List<String> issues) throws IOException {
        int annotatedClasses = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(jar))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory() && entry.getName().endsWith(".class")) {
                    AnnotationCheck check = inspectClass(zip.readAllBytes(), expectedApi, expectedSign);
                    annotatedClasses += check.annotated ? 1 : 0;
                    issues.addAll(check.issues);
                    if (expectedSign < 0 && isHeadlessModuleEntry(entry.getName()) && !check.annotated) {
                        issues.add(" - " + entry.getName().replace('/', '.').replaceFirst("\\.class$", "")
                            + " is missing @APIVersion; expected -" + expectedApi + " or 0");
                    }
                }
            }
        }
        return annotatedClasses;
    }

    private static boolean isHeadlessModuleEntry(String entryName) {
        String fileName = entryName.substring(entryName.lastIndexOf('/') + 1);
        return fileName.endsWith(".class")
            && !fileName.contains("$")
            && (fileName.startsWith("CloudScriptAction")
                || fileName.startsWith("CloudVariableProvider")
                || fileName.startsWith("CloudScriptedIterator")
                || fileName.startsWith("CloudEventProvider"));
    }

    private AnnotationCheck inspectClass(byte[] bytes, int expectedApi, int expectedSign) {
        AnnotationCheck check = new AnnotationCheck();
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            private String className;

            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                this.className = name;
            }

            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                if (!API_VERSION_ANNOTATION.equals(descriptor)) return null;
                check.annotated = true;
                return new AnnotationVisitor(Opcodes.ASM9) {
                    @Override
                    public void visit(String name, Object value) {
                        if (!"value".equals(name) || !(value instanceof Integer apiValue)) return;
                        if (!isCompatibleApiVersion(apiValue, expectedApi, expectedSign)) {
                            String expected = expectedSign < 0 ? "-" + expectedApi + " or 0"
                                : expectedSign > 0 ? String.valueOf(expectedApi)
                                : "+/-" + expectedApi;
                            check.issues.add(" - " + className + " has @APIVersion(" + apiValue + "), expected " + expected);
                        }
                    }
                };
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return check;
    }

    private static boolean isCompatibleApiVersion(int apiValue, int expectedApi, int expectedSign) {
        if (expectedSign < 0) return apiValue == -expectedApi || apiValue == 0;
        if (expectedSign > 0) return apiValue == expectedApi;
        return Math.abs(apiValue) == expectedApi;
    }

    private static final class AnnotationCheck {
        boolean annotated;
        final List<String> issues = new ArrayList<>();
    }
}
