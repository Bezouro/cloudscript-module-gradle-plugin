package br.com.cloudmc.gradle;

import com.sun.net.httpserver.HttpServer;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DeployCloudScriptModuleTaskTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void defaultDesktopUploadDoesNotRequestMicrocraftConversion() throws IOException {
        try (UploadServer server = new UploadServer()) {
            File project = project("default", server.url(), 18, true, false, false);
            run(project, "deployCloudScriptModule");

            assertEquals(1, server.bodies.size());
            String body = server.bodies.get(0);
            assertField(body, "platform", "desktop");
            assertFalse(body.contains("name=\"convertToMicrocraft\""));
            assertFalse(body.contains("name=\"replaceNativeMicrocraft\""));
        }
    }

    @Test
    public void conversionOptInDoesNotReplaceAnExistingNativeModule() throws IOException {
        try (UploadServer server = new UploadServer()) {
            File project = project("opt-in", server.url(), 18, true, true, false);
            run(project, "deployCloudScriptModule");

            assertEquals(1, server.bodies.size());
            String body = server.bodies.get(0);
            assertField(body, "platform", "desktop");
            assertField(body, "convertToMicrocraft", "true");
            assertFalse(body.contains("name=\"replaceNativeMicrocraft\""));
        }
    }

    @Test
    public void explicitReplacementIsSentWithDesktopUpload() throws IOException {
        try (UploadServer server = new UploadServer()) {
            File project = project("replace", server.url(), 18, true, true, true);
            run(project, "deployCloudScriptModule");

            assertEquals(1, server.bodies.size());
            String body = server.bodies.get(0);
            assertField(body, "platform", "desktop");
            assertField(body, "convertToMicrocraft", "true");
            assertField(body, "replaceNativeMicrocraft", "true");
        }
    }

    @Test
    public void invalidOptInCombinationsFailAtConfigurationTime() throws IOException {
        try (UploadServer server = new UploadServer()) {
            assertFailure(project("without-conversion", server.url(), 18, true, false, true),
                "replaceNativeMicrocraft requires convertDesktopToMicrocraft=true");
            assertFailure(project("wrong-api", server.url(), 10, true, true, false),
                "convertDesktopToMicrocraft requires CloudScript API 18");
            assertFailure(project("without-desktop", server.url(), 18, false, true, false),
                "convertDesktopToMicrocraft requires deployDesktop=true");
            assertEquals(0, server.bodies.size());
        }
    }

    private void assertFailure(File project, String expected) {
        BuildResult result = GradleRunner.create()
            .withProjectDir(project)
            .withPluginClasspath()
            .withArguments("help", "--offline", "--stacktrace")
            .buildAndFail();
        assertTrue(result.getOutput(), result.getOutput().contains(expected));
    }

    private BuildResult run(File project, String task) {
        return GradleRunner.create()
            .withProjectDir(project)
            .withPluginClasspath()
            .withArguments(task, "--offline", "--stacktrace")
            .build();
    }

    private File project(String name, String baseUrl, int apiVersion, boolean deployDesktop,
                         boolean convertToMicrocraft, boolean replaceNativeMicrocraft) throws IOException {
        File dir = temporaryFolder.newFolder(name);
        Files.writeString(dir.toPath().resolve("settings.gradle"), "rootProject.name = 'probe'\n");
        String script = "plugins { id 'br.com.cloudmc.cloudscript-module' }\n" +
            "cloudScriptModule {\n" +
            "  apiVersion.set(" + apiVersion + ")\n" +
            "  moduleName.set('Probe')\n" +
            "  addStubDependency.set(false)\n" +
            "  addCloudScriptStubDependency.set(false)\n" +
            "  deployBaseUrl.set('" + baseUrl + "')\n" +
            "  deployToken.set('test-token')\n" +
            "  deployDesktop.set(" + deployDesktop + ")\n" +
            "  deployCloudMc.set(false)\n" +
            "  convertDesktopToMicrocraft.set(" + convertToMicrocraft + ")\n" +
            "  replaceNativeMicrocraft.set(" + replaceNativeMicrocraft + ")\n" +
            "}\n";
        Files.writeString(dir.toPath().resolve("build.gradle"), script);
        Path resource = dir.toPath().resolve("src/main/resources/example/CloudScriptActionProbe.class");
        Files.createDirectories(resource.getParent());
        Files.write(resource, actionClass(apiVersion));
        return dir;
    }

    private byte[] actionClass(int apiVersion) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "example/CloudScriptActionProbe", null, "java/lang/Object", null);
        var annotation = writer.visitAnnotation("Lnet/eq2online/macros/scripting/api/APIVersion;", true);
        annotation.visit("value", apiVersion);
        annotation.visitEnd();
        MethodVisitor constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(1, 1);
        constructor.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private void assertField(String body, String field, String value) {
        assertTrue(body, body.contains("name=\"" + field + "\"\r\n\r\n" + value + "\r\n"));
    }

    private static final class UploadServer implements AutoCloseable {
        private final HttpServer server;
        private final List<String> bodies = new CopyOnWriteArrayList<>();

        private UploadServer() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/api/modules/upload", exchange -> {
                byte[] request = exchange.getRequestBody().readAllBytes();
                bodies.add(new String(request, StandardCharsets.ISO_8859_1));
                byte[] response = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
                exchange.close();
            });
            server.start();
        }

        private String url() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
