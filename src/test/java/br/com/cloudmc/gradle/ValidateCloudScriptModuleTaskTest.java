package br.com.cloudmc.gradle;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ValidateCloudScriptModuleTaskTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void headlessRuntimeAcceptsNegativeAndZeroButRejectsPositive() throws IOException {
        validate(-1, -18);
        validate(-1, 0);
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> validate(-1, 18));
        assertTrue(error.getMessage().contains("@APIVersion(18), expected -18 or 0"));
    }

    @Test
    public void desktopRuntimeRequiresPositiveVersion() throws IOException {
        validate(1, 18);
        assertThrows(IllegalStateException.class, () -> validate(1, -18));
        assertThrows(IllegalStateException.class, () -> validate(1, 0));
    }

    @Test
    public void legacySharedArtifactKeepsAbsoluteVersionCheck() throws IOException {
        validate(0, 18);
        validate(0, -18);
        assertThrows(IllegalStateException.class, () -> validate(0, 0));
    }

    private void validate(int expectedSign, int annotatedVersion) throws IOException {
        File projectDirectory = temporaryFolder.newFolder();
        File jar = new File(projectDirectory, "module.jar");
        try (JarOutputStream output = new JarOutputStream(new FileOutputStream(jar))) {
            output.putNextEntry(new JarEntry("example/CloudScriptActionProbe.class"));
            output.write(annotatedClass(annotatedVersion));
            output.closeEntry();
        }

        Project project = ProjectBuilder.builder().withProjectDir(projectDirectory).build();
        ValidateCloudScriptModuleTask task = project.getTasks().create("validateProbe", ValidateCloudScriptModuleTask.class);
        task.getApiVersion().set(18);
        task.getExpectedApiSign().set(expectedSign);
        task.getModuleJar().set(jar);
        task.run();
    }

    private byte[] annotatedClass(int apiVersion) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "example/CloudScriptActionProbe", null, "java/lang/Object", null);
        AnnotationVisitor annotation = writer.visitAnnotation("Lnet/eq2online/macros/scripting/api/APIVersion;", true);
        annotation.visit("value", apiVersion);
        annotation.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }
}
