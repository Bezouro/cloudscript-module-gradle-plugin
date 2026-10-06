package br.com.cloudmc.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@DisableCachingByDefault(because = "Deploy mutates backend state.")
public abstract class DeployCloudScriptModuleTask extends DefaultTask {
    @Input
    public abstract Property<Integer> getApiVersion();

    @Input
    public abstract Property<String> getBaseUrl();

    @Internal
    public abstract Property<String> getToken();

    @Input
    public abstract Property<String> getModuleName();

    @Input
    public abstract Property<Boolean> getDeployDesktop();

    @Input
    public abstract Property<Boolean> getDeployCloudMc();

    @Input
    public abstract Property<Boolean> getConvertDesktopToMicrocraft();

    @Input
    public abstract Property<Boolean> getReplaceNativeMicrocraft();

    @InputFile
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getDesktopJar();

    @InputFile
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getCloudMcJar();

    public DeployCloudScriptModuleTask() {
        getConvertDesktopToMicrocraft().convention(false);
        getReplaceNativeMicrocraft().convention(false);
    }

    @TaskAction
    public void run() throws IOException, InterruptedException {
        String token = getToken().getOrElse("").trim();
        if (token.isEmpty()) {
            throw new IllegalStateException("Missing CloudScript deploy token. Set cloudScriptModule.deployToken, -PcloudScriptToken=..., or CLOUDSCRIPT_TOKEN.");
        }
        if (getReplaceNativeMicrocraft().get() && !getConvertDesktopToMicrocraft().get()) {
            throw new IllegalStateException("replaceNativeMicrocraft requires convertDesktopToMicrocraft=true");
        }
        if (getConvertDesktopToMicrocraft().get() && getApiVersion().get() != 18) {
            throw new IllegalStateException("convertDesktopToMicrocraft requires CloudScript API 18");
        }
        if (getConvertDesktopToMicrocraft().get() && !getDeployDesktop().get()) {
            throw new IllegalStateException("convertDesktopToMicrocraft requires deployDesktop=true");
        }

        List<String> deployed = new ArrayList<>();
        if (getDeployDesktop().get()) {
            upload("desktop", getDesktopJar().get().getAsFile().toPath(), false,
                getConvertDesktopToMicrocraft().get(), getReplaceNativeMicrocraft().get(), token);
            deployed.add("desktop");
        }
        if (getDeployCloudMc().get()) {
            upload("cloudmc", getCloudMcJar().get().getAsFile().toPath(), false, false, false, token);
            deployed.add("cloudmc");
        }
        if (deployed.isEmpty()) {
            throw new IllegalStateException("Nothing to deploy. Enable deployDesktop or deployCloudMc.");
        }

        getLogger().lifecycle("[CloudScript] Deployed module {} variants: {}", getModuleName().get(), String.join(", ", deployed));
    }

    private void upload(String platform, java.nio.file.Path jar, boolean convertToCloudMc,
                        boolean convertToMicrocraft, boolean replaceNativeMicrocraft, String token) throws IOException, InterruptedException {
        String boundary = "----CloudScriptGradle" + UUID.randomUUID().toString().replace("-", "");
        String filename = ModuleNames.uploadFileName(getModuleName().get(), getApiVersion().get(), platform);
        byte[] body = multipart(boundary, platform, convertToCloudMc, convertToMicrocraft,
            replaceNativeMicrocraft, filename, Files.readAllBytes(jar));

        URI uri = URI.create(trimTrailingSlash(getBaseUrl().get()) + "/api/modules/upload");
        HttpRequest request = HttpRequest.newBuilder(uri)
            .header("Authorization", token)
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .build();

        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IOException("CloudScript deploy failed for " + platform + " with HTTP " + response.statusCode() + ": " + response.body());
        }
        getLogger().lifecycle("[CloudScript] Uploaded {} module jar {}", platform, jar);
    }

    private byte[] multipart(String boundary, String platform, boolean convertToCloudMc,
                             boolean convertToMicrocraft, boolean replaceNativeMicrocraft,
                             String filename, byte[] jar) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeField(out, boundary, "platform", platform);
        writeField(out, boundary, "convertToCloudMc", Boolean.toString(convertToCloudMc));
        if (convertToMicrocraft) writeField(out, boundary, "convertToMicrocraft", "true");
        if (replaceNativeMicrocraft) writeField(out, boundary, "replaceNativeMicrocraft", "true");
        write(out, "--" + boundary + "\r\n");
        write(out, "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename.replace("\"", "_") + "\"\r\n");
        write(out, "Content-Type: application/java-archive\r\n\r\n");
        out.write(jar);
        write(out, "\r\n--" + boundary + "--\r\n");
        return out.toByteArray();
    }

    private void writeField(ByteArrayOutputStream out, String boundary, String name, String value) throws IOException {
        write(out, "--" + boundary + "\r\n");
        write(out, "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n");
        write(out, value + "\r\n");
    }

    private void write(ByteArrayOutputStream out, String value) throws IOException {
        out.write(value.getBytes(StandardCharsets.UTF_8));
    }

    private String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
