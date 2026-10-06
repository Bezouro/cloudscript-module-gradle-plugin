package br.com.cloudmc.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.jvm.tasks.Jar;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class CloudScriptModulePlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getPluginManager().apply("java");

        CloudScriptModuleExtension extension = project.getExtensions().create(
            "cloudScriptModule",
            CloudScriptModuleExtension.class
        );
        extension.getDeployToken().convention(
            project.getProviders().environmentVariable("CLOUDSCRIPT_TOKEN")
                .orElse(project.getProviders().gradleProperty("cloudScriptToken"))
        );
        extension.getModuleName().convention(project.getName());

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
            String architecture = extension.getArchitecture().get().trim().toLowerCase(Locale.ROOT);
            if ("multi-runtime".equals(architecture) || "multiruntime".equals(architecture)) {
                configureMultiRuntime(project, extension, stubs);
                return;
            }
            if (!"normal".equals(architecture)) {
                throw new IllegalArgumentException("Unsupported CloudScript module architecture '" + architecture + "'; expected normal or multi-runtime");
            }

            int apiVersion = extension.getApiVersion().get();
            if (apiVersion != 10 && apiVersion != 18 && apiVersion != 26) {
                throw new IllegalArgumentException("Unsupported CloudScript API " + apiVersion + "; expected 10, 18 or 26");
            }

            String moduleName = extension.getModuleName().get();
            String minecraftVersion = extension.getMinecraftVersion().getOrElse(defaultMinecraftVersion(apiVersion));
            if (apiVersion == 26 && (extension.getSetupWorkspace().get() || extension.getUseWorkspaceClasspath().get())) {
                throw new IllegalArgumentException("CloudScript API 26 does not support setupCloudScriptWorkspace yet. Use published stubs or provide your own compileOnly jars.");
            }
            TaskProvider<SetupCloudScriptWorkspaceTask> setupWorkspace = project.getTasks().register(
                "setupCloudScriptWorkspace",
                SetupCloudScriptWorkspaceTask.class,
                task -> {
                    task.setGroup("CloudScript");
                    task.setDescription("Downloads and prepares the Minecraft/LiteLoader/Macro Keybind workspace jars.");
                    task.getApiVersion().set(apiVersion);
                    task.getMinecraftVersion().set(minecraftVersion);
                    task.getModernMinecraftNames().set(extension.getModernMinecraftNames());
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
                if (extension.getAddCloudMcStubDependency().get()) {
                    project.getDependencies().add("compileOnly", dependency);
                }
            }
            if (extension.getAddCloudScriptStubDependency().get()) {
                project.getDependencies().add(
                    "compileOnly",
                    "com.bezouro.modules.cloudscript:cloudscript-dev-api" + apiVersion + "-stubs:" + extension.getStubsVersion().get()
                );
            }

            SourceSet mainSourceSet = project.getExtensions().getByType(JavaPluginExtension.class)
                .getSourceSets()
                .getByName(SourceSet.MAIN_SOURCE_SET_NAME);
            TaskProvider<Jar> moduleJar = project.getTasks().named(extension.getModuleJarTaskName().get(), Jar.class);
            TaskProvider<ObfuscateDesktopModuleTask> obfuscateDesktop = project.getTasks().register("obfuscateDesktopModule", ObfuscateDesktopModuleTask.class, task -> {
                task.setGroup("CloudScript");
                task.setDescription("Builds the desktop Minecraft module jar, remapping MCP names back to notch names.");
                task.getApiVersion().set(apiVersion);
                task.getModernMinecraftNames().set(extension.getModernMinecraftNames());
                task.getInputJar().set(moduleJar.flatMap(Jar::getArchiveFile));
                task.getRemapClasspath().from(mainSourceSet.getCompileClasspath());
                task.getOutputJar().set(project.getLayout().getBuildDirectory().file(
                    "libs/" + ModuleNames.artifactName(moduleName, apiVersion, "desktop")
                ));
            });

            TaskProvider<ValidateDesktopModuleTask> validateDesktop = project.getTasks().register("validateDesktopModule", ValidateDesktopModuleTask.class, task -> {
                task.setGroup("CloudScript");
                task.setDescription("Validates the desktop Minecraft module jar has no leftover API 10 deobfuscated names.");
                task.getApiVersion().set(apiVersion);
                task.getModuleJar().set(obfuscateDesktop.flatMap(ObfuscateDesktopModuleTask::getOutputJar));
                task.getRemapClasspath().from(mainSourceSet.getCompileClasspath());
            });

            TaskProvider<RemapCloudMcModuleTask> remap = project.getTasks().register("remapCloudMcModule", RemapCloudMcModuleTask.class, task -> {
                task.setGroup("CloudScript");
                task.setDescription("Builds the CloudMC module jar, remapping API 10 class names when required.");
                task.getApiVersion().set(apiVersion);
                task.getInputJar().set(moduleJar.flatMap(Jar::getArchiveFile));
                task.getOutputJar().set(project.getLayout().getBuildDirectory().file(
                    "intermediates/cloudscript/cloudmc-remapped/" + ModuleNames.artifactName(moduleName, apiVersion, "cloudmc")
                ));
            });

            TaskProvider<GenerateCloudMcNativeMetadataTask> nativeCloudMc = project.getTasks().register(
                "generateCloudMcNativeMetadata",
                GenerateCloudMcNativeMetadataTask.class,
                task -> {
                    task.setGroup("CloudScript");
                    task.setDescription("Adds CloudMC native-image metadata to the CloudMC module jar.");
                    task.getApiVersion().set(apiVersion);
                    task.getModuleName().set(extension.getModuleName());
                    task.getNativeMetadataEnabled().set(extension.getNativeMetadata());
                    task.getIncludeModuleResources().set(extension.getNativeIncludeModuleResources());
                    task.getAdditionalReflectClasses().set(extension.getNativeReflectClasses());
                    task.getAdditionalResourcePatterns().set(extension.getNativeResourcePatterns());
                    task.getInputJar().set(remap.flatMap(RemapCloudMcModuleTask::getOutputJar));
                    task.getOutputJar().set(project.getLayout().getBuildDirectory().file(
                        "libs/" + ModuleNames.artifactName(moduleName, apiVersion, "cloudmc")
                    ));
                }
            );

            TaskProvider<ValidateCloudMcModuleTask> validate = project.getTasks().register("validateCloudMcModule", ValidateCloudMcModuleTask.class, task -> {
                task.setGroup("CloudScript");
                task.setDescription("Validates the CloudMC module jar against the current CloudMC stubs.");
                task.getApiVersion().set(apiVersion);
                task.getModuleJar().set(nativeCloudMc.flatMap(GenerateCloudMcNativeMetadataTask::getOutputJar));
                task.getStubClasspath().from(stubs);
            });

            TaskProvider<ValidateCloudScriptModuleTask> validateCloudScript = project.getTasks().register("validateCloudScriptModule", ValidateCloudScriptModuleTask.class, task -> {
                task.setGroup("CloudScript");
                task.setDescription("Validates CloudScript-specific module rules.");
                task.getApiVersion().set(apiVersion);
                task.getModuleJar().set(moduleJar.flatMap(Jar::getArchiveFile));
            });

            TaskProvider<ValidateCloudScriptModuleTask> validateCloudMcAnnotations = project.getTasks().register("validateCloudMcApiAnnotations", ValidateCloudScriptModuleTask.class, task -> {
                task.setGroup("CloudScript");
                task.setDescription("Validates API annotations in the final CloudMC module jar.");
                task.getApiVersion().set(apiVersion);
                task.getExpectedApiSign().set(-1);
                task.getModuleJar().set(nativeCloudMc.flatMap(GenerateCloudMcNativeMetadataTask::getOutputJar));
            });

            project.getTasks().register("buildCloudMcModule", task -> {
                task.setGroup("CloudScript");
                task.setDescription("Builds and validates the CloudMC module jar.");
                task.dependsOn(validate, validateCloudMcAnnotations);
            });

            project.getTasks().register("buildDesktopModule", task -> {
                task.setGroup("CloudScript");
                task.setDescription("Builds the desktop Minecraft obfuscated module jar.");
                task.dependsOn(validateDesktop);
            });

            project.getTasks().register("buildCloudScriptModule", task -> {
                task.setGroup("CloudScript");
                task.setDescription(apiVersion == 26
                    ? "Builds the CloudMC module jar for API 26."
                    : "Builds the desktop and CloudMC module jars.");
                if (apiVersion != 26) {
                    task.dependsOn(validateDesktop);
                }
                task.dependsOn(validate, validateCloudScript, validateCloudMcAnnotations);
            });

            project.getTasks().register("deployCloudScriptModule", DeployCloudScriptModuleTask.class, task -> {
                task.setGroup("CloudScript");
                task.setDescription("Builds, validates and uploads the module to CloudScript.");
                if (extension.getDeployDesktop().get()) {
                    task.dependsOn(validateDesktop);
                }
                if (extension.getDeployCloudMc().get()) {
                    task.dependsOn(validate, validateCloudMcAnnotations);
                }
                if (extension.getDeployDesktop().get()) {
                    task.dependsOn(validateCloudScript);
                }
                task.getApiVersion().set(apiVersion);
                task.getBaseUrl().set(extension.getDeployBaseUrl());
                task.getToken().set(extension.getDeployToken());
                task.getModuleName().set(extension.getModuleName());
                task.getDeployDesktop().set(extension.getDeployDesktop());
                task.getDeployCloudMc().set(extension.getDeployCloudMc());
                task.getDesktopJar().set(obfuscateDesktop.flatMap(ObfuscateDesktopModuleTask::getOutputJar));
                task.getCloudMcJar().set(nativeCloudMc.flatMap(GenerateCloudMcNativeMetadataTask::getOutputJar));
            });

            if (extension.getAttachToBuild().get()) {
                project.getTasks().named("build").configure(task -> {
                    if (apiVersion != 26) {
                        task.dependsOn(validateDesktop);
                    }
                    task.dependsOn(validate, validateCloudScript, validateCloudMcAnnotations);
                });
            }
        });
    }

    private void configureMultiRuntime(Project project, CloudScriptModuleExtension extension, Configuration legacyStubs) {
        String moduleName = extension.getModuleName().get();
        Map<String, RuntimeTarget> supported = supportedRuntimeTargets();
        SourceSetContainer sourceSets = project.getExtensions().getByType(JavaPluginExtension.class).getSourceSets();
        java.util.List<TaskProvider<? extends Task>> buildTasks = new java.util.ArrayList<>();

        for (String requestedRuntime : extension.getRuntimes().get()) {
            RuntimeTarget target = supported.get(normalizeRuntime(requestedRuntime));
            if (target == null) {
                throw new IllegalArgumentException("Unsupported CloudScript runtime '" + requestedRuntime + "'. Supported runtimes: " + String.join(", ", supported.keySet()));
            }
            validateApiVersion(target.apiVersion);

            SourceSet sourceSet = sourceSets.create(target.sourceSetName, source -> {
                source.getJava().srcDir("src/common/java");
                source.getJava().srcDir("src/" + target.runtimeName + "/java");
                source.getResources().srcDir("src/common/resources");
                source.getResources().srcDir("src/" + target.runtimeName + "/resources");
            });

            addTargetStubDependencies(project, extension, sourceSet, target);

            TaskProvider<Jar> rawJar = project.getTasks().register("jar" + target.capitalized + "Module", Jar.class, task -> {
                task.setGroup("CloudScript");
                task.setDescription("Builds the raw CloudScript module jar for " + target.runtimeName + ".");
                task.from(sourceSet.getOutput());
                task.getArchiveBaseName().set(ModuleNames.stripJarSuffix(ModuleNames.artifactName(moduleName, target.apiVersion, target.runtimeName + "-raw")));
                task.getArchiveVersion().set("");
                task.getDestinationDirectory().set(project.getLayout().getBuildDirectory().dir("intermediates/cloudscript/" + target.runtimeName + "/raw"));
            });

            TaskProvider<? extends Task> artifactTask = rawJar;
            org.gradle.api.provider.Provider<org.gradle.api.file.RegularFile> artifactFile = rawJar.flatMap(Jar::getArchiveFile);

            if (target.obfuscateMinecraft) {
                TaskProvider<ObfuscateDesktopModuleTask> obfuscate = project.getTasks().register("obfuscate" + target.capitalized + "Module", ObfuscateDesktopModuleTask.class, task -> {
                    task.setGroup("CloudScript");
                    task.setDescription("Builds the " + target.runtimeName + " module jar, remapping Minecraft MCP names back to notch names.");
                    task.getApiVersion().set(target.apiVersion);
                    task.getModernMinecraftNames().set(target.apiVersion == 10 || extension.getModernMinecraftNames().get());
                    task.getInputJar().set(rawJar.flatMap(Jar::getArchiveFile));
                    task.getRemapClasspath().from(sourceSet.getCompileClasspath());
                    task.getOutputJar().set(project.getLayout().getBuildDirectory().file(
                        "libs/" + ModuleNames.artifactName(moduleName, target.apiVersion, target.runtimeName)
                    ));
                });

                TaskProvider<ValidateDesktopModuleTask> validateDesktop = project.getTasks().register("validate" + target.capitalized + "MinecraftObfuscation", ValidateDesktopModuleTask.class, task -> {
                    task.setGroup("CloudScript");
                    task.setDescription("Validates the " + target.runtimeName + " Minecraft obfuscation result.");
                    task.getApiVersion().set(target.apiVersion);
                    task.getModuleJar().set(obfuscate.flatMap(ObfuscateDesktopModuleTask::getOutputJar));
                    task.getRemapClasspath().from(sourceSet.getCompileClasspath());
                });
                artifactTask = validateDesktop;
                artifactFile = obfuscate.flatMap(ObfuscateDesktopModuleTask::getOutputJar);
            } else {
                rawJar.configure(task -> {
                    task.getArchiveBaseName().set(ModuleNames.stripJarSuffix(ModuleNames.artifactName(moduleName, target.apiVersion, target.runtimeName)));
                    task.getDestinationDirectory().set(project.getLayout().getBuildDirectory().dir("libs"));
                });
            }

            org.gradle.api.provider.Provider<org.gradle.api.file.RegularFile> finalArtifactFile = artifactFile;
            TaskProvider<? extends Task> finalArtifactTask = artifactTask;
            TaskProvider<ValidateCloudScriptModuleTask> validateCloudScript = project.getTasks().register("validate" + target.capitalized + "CloudScriptModule", ValidateCloudScriptModuleTask.class, task -> {
                task.setGroup("CloudScript");
                task.setDescription("Validates CloudScript-specific rules for " + target.runtimeName + ".");
                task.getApiVersion().set(target.apiVersion);
                task.getExpectedApiSign().set(target.obfuscateMinecraft ? 1 : -1);
                task.getModuleJar().set(finalArtifactFile);
            });
            validateCloudScript.configure(task -> task.dependsOn(finalArtifactTask));

            TaskProvider<Task> buildRuntime = project.getTasks().register("build" + target.capitalized + "Module", task -> {
                task.setGroup("CloudScript");
                task.setDescription("Builds and validates the CloudScript module for " + target.runtimeName + ".");
                task.dependsOn(validateCloudScript);
            });
            buildTasks.add(buildRuntime);
        }

        project.getTasks().register("buildCloudScriptModule", task -> {
            task.setGroup("CloudScript");
            task.setDescription("Builds all configured CloudScript runtime module jars.");
            buildTasks.forEach(task::dependsOn);
        });

        project.getTasks().register("deployCloudScriptModule", task -> {
            task.setGroup("CloudScript");
            task.setDescription("Multi-runtime deploy is not wired yet; use buildCloudScriptModule and upload the generated runtime jars.");
            task.doFirst(ignored -> {
                throw new UnsupportedOperationException("deployCloudScriptModule does not support architecture=multi-runtime yet. Use buildCloudScriptModule and publish the generated runtime jars.");
            });
        });

        if (extension.getAttachToBuild().get()) {
            project.getTasks().named("build").configure(task -> buildTasks.forEach(task::dependsOn));
        }
    }

    private void addTargetStubDependencies(Project project, CloudScriptModuleExtension extension, SourceSet sourceSet, RuntimeTarget target) {
        String stubsVersion = extension.getStubsVersion().get();
        if (extension.getAddStubDependency().get() && extension.getAddCloudMcStubDependency().get()) {
            project.getDependencies().add(
                sourceSet.getCompileOnlyConfigurationName(),
                "br.com.cloudmc:cloudmc-api" + target.apiVersion + "-stubs:" + stubsVersion
            );
        }
        if (extension.getAddCloudScriptStubDependency().get()) {
            project.getDependencies().add(
                sourceSet.getCompileOnlyConfigurationName(),
                "com.bezouro.modules.cloudscript:cloudscript-dev-api" + target.apiVersion + "-stubs:" + stubsVersion
            );
        }
    }

    private void validateApiVersion(int apiVersion) {
        if (apiVersion != 10 && apiVersion != 18 && apiVersion != 26) {
            throw new IllegalArgumentException("Unsupported CloudScript API " + apiVersion + "; expected 10, 18 or 26");
        }
    }

    private Map<String, RuntimeTarget> supportedRuntimeTargets() {
        Map<String, RuntimeTarget> targets = new LinkedHashMap<>();
        addRuntimeTarget(targets, new RuntimeTarget("desktop15", 10, true));
        addRuntimeTarget(targets, new RuntimeTarget("desktop18", 18, true));
        addRuntimeTarget(targets, new RuntimeTarget("minicraft15", 10, false));
        addRuntimeTarget(targets, new RuntimeTarget("minicraft18", 18, false));
        addRuntimeTarget(targets, new RuntimeTarget("microcraft", 26, false));
        return targets;
    }

    private void addRuntimeTarget(Map<String, RuntimeTarget> targets, RuntimeTarget target) {
        targets.put(target.runtimeName, target);
    }

    private String normalizeRuntime(String runtime) {
        return runtime.trim().toLowerCase(Locale.ROOT)
            .replace("-", "")
            .replace("_", "")
            .replace(".", "");
    }

    private static final class RuntimeTarget {
        final String runtimeName;
        final int apiVersion;
        final boolean obfuscateMinecraft;
        final String capitalized;
        final String sourceSetName;

        RuntimeTarget(String runtimeName, int apiVersion, boolean obfuscateMinecraft) {
            this.runtimeName = runtimeName;
            this.apiVersion = apiVersion;
            this.obfuscateMinecraft = obfuscateMinecraft;
            this.capitalized = Character.toUpperCase(runtimeName.charAt(0)) + runtimeName.substring(1);
            this.sourceSetName = "cloudScript" + capitalized;
        }
    }

    private String defaultMinecraftVersion(int apiVersion) {
        if (apiVersion == 10) return "1.5.2";
        if (apiVersion == 18) return "1.8";
        if (apiVersion == 26) return "1.12.1";
        throw new IllegalArgumentException("Unsupported CloudScript API " + apiVersion);
    }
}
