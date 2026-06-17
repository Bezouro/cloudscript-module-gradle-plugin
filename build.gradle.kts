plugins {
    `java-gradle-plugin`
    `maven-publish`
}

group = "br.com.cloudmc"
version = providers.gradleProperty("projectVersion")
    .orElse("0.1.0-SNAPSHOT")
    .map { it.removePrefix("v") }
    .get()

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

gradlePlugin {
    plugins {
        create("cloudScriptModule") {
            id = "br.com.cloudmc.cloudscript-module"
            implementationClass = "br.com.cloudmc.gradle.CloudScriptModulePlugin"
            displayName = "CloudScript Module Gradle Plugin"
            description = "Builds and validates CloudScript modules for Desktop Minecraft and CloudMC."
        }
    }
}

dependencies {
    implementation("org.ow2.asm:asm:9.7.1")
    implementation("org.ow2.asm:asm-commons:9.7.1")
    implementation("org.ow2.asm:asm-tree:9.7.1")
}

publishing {
    repositories {
        maven {
            name = "PublicMaven"
            url = layout.buildDirectory.dir("public-maven").get().asFile.toURI()
        }
    }
}
