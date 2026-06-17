package br.com.cloudmc.gradle;

import org.gradle.api.model.ObjectFactory;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;

import javax.inject.Inject;

public abstract class CloudScriptModuleExtension {
    private final Property<Integer> apiVersion;
    private final Property<String> stubsVersion;
    private final Property<Boolean> addStubDependency;
    private final Property<Boolean> addCloudScriptStubDependency;
    private final Property<Boolean> attachToBuild;
    private final Property<String> moduleJarTaskName;
    private final Property<Boolean> setupWorkspace;
    private final Property<Boolean> useWorkspaceClasspath;
    private final Property<String> minecraftVersion;
    private final RegularFileProperty liteLoaderJar;
    private final RegularFileProperty macroKeybindJar;
    private final Property<String> liteLoaderUrl;
    private final Property<String> macroKeybindUrl;
    private final Property<String> deployBaseUrl;
    private final Property<String> deployToken;
    private final Property<String> moduleName;
    private final Property<Boolean> deployDesktop;
    private final Property<Boolean> deployCloudMc;

    @Inject
    public CloudScriptModuleExtension(ObjectFactory objects) {
        this.apiVersion = objects.property(Integer.class).convention(18);
        this.stubsVersion = objects.property(String.class).convention("latest.release");
        this.addStubDependency = objects.property(Boolean.class).convention(true);
        this.addCloudScriptStubDependency = objects.property(Boolean.class).convention(true);
        this.attachToBuild = objects.property(Boolean.class).convention(true);
        this.moduleJarTaskName = objects.property(String.class).convention("jar");
        this.setupWorkspace = objects.property(Boolean.class).convention(false);
        this.useWorkspaceClasspath = objects.property(Boolean.class).convention(false);
        this.minecraftVersion = objects.property(String.class);
        this.liteLoaderJar = objects.fileProperty();
        this.macroKeybindJar = objects.fileProperty();
        this.liteLoaderUrl = objects.property(String.class);
        this.macroKeybindUrl = objects.property(String.class);
        this.deployBaseUrl = objects.property(String.class).convention("https://cloudscript.bezouro.com.br");
        this.deployToken = objects.property(String.class);
        this.moduleName = objects.property(String.class);
        this.deployDesktop = objects.property(Boolean.class).convention(true);
        this.deployCloudMc = objects.property(Boolean.class).convention(true);
    }

    public Property<Integer> getApiVersion() {
        return apiVersion;
    }

    public Property<String> getStubsVersion() {
        return stubsVersion;
    }

    public Property<Boolean> getAddStubDependency() {
        return addStubDependency;
    }

    public Property<Boolean> getAddCloudScriptStubDependency() {
        return addCloudScriptStubDependency;
    }

    public Property<Boolean> getAttachToBuild() {
        return attachToBuild;
    }

    public Property<String> getModuleJarTaskName() {
        return moduleJarTaskName;
    }

    public Property<Boolean> getSetupWorkspace() {
        return setupWorkspace;
    }

    public Property<Boolean> getUseWorkspaceClasspath() {
        return useWorkspaceClasspath;
    }

    public Property<String> getMinecraftVersion() {
        return minecraftVersion;
    }

    public RegularFileProperty getLiteLoaderJar() {
        return liteLoaderJar;
    }

    public RegularFileProperty getMacroKeybindJar() {
        return macroKeybindJar;
    }

    public Property<String> getLiteLoaderUrl() {
        return liteLoaderUrl;
    }

    public Property<String> getMacroKeybindUrl() {
        return macroKeybindUrl;
    }

    public Property<String> getDeployBaseUrl() {
        return deployBaseUrl;
    }

    public Property<String> getDeployToken() {
        return deployToken;
    }

    public Property<String> getModuleName() {
        return moduleName;
    }

    public Property<Boolean> getDeployDesktop() {
        return deployDesktop;
    }

    public Property<Boolean> getDeployCloudMc() {
        return deployCloudMc;
    }
}
