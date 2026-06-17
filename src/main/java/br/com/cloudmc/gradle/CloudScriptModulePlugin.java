package br.com.cloudmc.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.jvm.tasks.Jar;

public class CloudScriptModulePlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getPluginManager().apply("java");

        CloudScriptModuleExtension extension = project.getExtensions().create(
            "cloudScriptModule",
            CloudScriptModuleExtension.class
        );

        Configuration stubs = project.getConfigurations().create("cloudMcStubs", configuration -> {
            configuration.setCanBeConsumed(false);
            configuration.setCanBeResolved(true);
            configuration.setDescription("CloudMC compile-only stubs used for module validation.");
        });

        project.getConfigurations().configureEach(configuration ->
            configuration.getResolutionStrategy().cacheDynamicVersionsFor(0, java.util.concurrent.TimeUnit.SECONDS)
        );

        project.getRepositories().maven(repository -> {
            repository.setName("CloudMCPublicMaven");
            repository.setUrl(project.uri("https://bezouro.github.io/Minicraft/maven"));
        });
        project.getRepositories().maven(repository -> {
            repository.setName("CloudScriptPublicMaven");
            repository.setUrl(project.uri("https://bezouro.github.io/CloudScriptJava/maven"));
        });

        project.afterEvaluate(ignored -> {
            int apiVersion = extension.getApiVersion().get();
            if (apiVersion != 10 && apiVersion != 18) {
                throw new IllegalArgumentException("Unsupported CloudScript API " + apiVersion + "; expected 10 or 18");
            }

            String minecraftVersion = extension.getMinecraftVersion().getOrElse(apiVersion == 10 ? "1.5.2" : "1.8");
            TaskProvider<SetupCloudScriptWorkspaceTask> setupWorkspace = project.getTasks().register(
                "setupCloudScriptWorkspace",
                SetupCloudScriptWorkspaceTask.class,
                task -> {
                    task.setGroup("CloudScript");
                    task.setDescription("Downloads and prepares the Minecraft/LiteLoader/Macro Keybind workspace jars.");
                    task.getApiVersion().set(apiVersion);
                    task.getMinecraftVersion().set(minecraftVersion);
                    task.getOutputDirectory().set(project.getLayout().getBuildDirectory().dir("cloudscript-workspace/api" + apiVersion));
                    task.getLiteLoaderJar().set(extension.getLiteLoaderJar());
                    task.getMacroKeybindJar().set(extension.getMacroKeybindJar());
                    task.getLiteLoaderUrl().set(extension.getLiteLoaderUrl());
                    task.getMacroKeybindUrl().set(extension.getMacroKeybindUrl());
                }
            );

            if (extension.getUseWorkspaceClasspath().get()) {
                ConfigurableFileCollection workspaceClasspath = project.files(
                    setupWorkspace.flatMap(SetupCloudScriptWorkspaceTask::getMinecraftDeobfJar)
                );
                workspaceClasspath.builtBy(setupWorkspace);
                project.getDependencies().add("compileOnly", workspaceClasspath);
            }

            if (extension.getSetupWorkspace().get()) {
                project.getTasks().named("compileJava").configure(task -> task.dependsOn(setupWorkspace));
            }

            if (extension.getAddStubDependency().get()) {
                String dependency = "br.com.cloudmc:cloudmc-api" + apiVersion + "-stubs:" + extension.getStubsVersion().get();
                project.getDependencies().add("cloudMcStubs", dependency);
                project.getDependencies().add("compileOnly", dependency);
            }
            if (extension.getAddCloudScriptStubDependency().get()) {
                project.getDependencies().add(
                    "compileOnly",
                    "com.bezouro.modules.cloudscript:cloudscript-dev-api" + apiVersion + "-stubs:" + extension.getStubsVersion().get()
                );
            }

            TaskProvider<Jar> moduleJar = project.getTasks().named(extension.getModuleJarTaskName().get(), Jar.class);
            TaskProvider<RemapCloudMcModuleTask> remap = project.getTasks().register("remapCloudMcModule", RemapCloudMcModuleTask.class, task -> {
                task.setGroup("CloudScript");
                task.setDescription("Builds the CloudMC module jar, remapping API 10 class names when required.");
                task.getApiVersion().set(apiVersion);
                task.getInputJar().set(moduleJar.flatMap(Jar::getArchiveFile));
                task.getOutputJar().set(project.getLayout().getBuildDirectory().file(
                    "libs/" + project.getName() + "-Api" + apiVersion + "-cloudmc.jar"
                ));
            });

            TaskProvider<ValidateCloudMcModuleTask> validate = project.getTasks().register("validateCloudMcModule", ValidateCloudMcModuleTask.class, task -> {
                task.setGroup("CloudScript");
                task.setDescription("Validates the CloudMC module jar against the current CloudMC stubs.");
                task.getApiVersion().set(apiVersion);
                task.getModuleJar().set(remap.flatMap(RemapCloudMcModuleTask::getOutputJar));
                task.getStubClasspath().from(stubs);
            });

            project.getTasks().register("buildCloudMcModule", task -> {
                task.setGroup("CloudScript");
                task.setDescription("Builds and validates the CloudMC module jar.");
                task.dependsOn(validate);
            });

            if (extension.getAttachToBuild().get()) {
                project.getTasks().named("build").configure(task -> task.dependsOn(validate));
            }
        });
    }
}
