pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven("https://bezouro.github.io/CloudScriptJava/maven")
        maven("https://bezouro.github.io/Minicraft/maven")
        maven("https://bezouro.github.io/microcraft/maven")
    }
}

rootProject.name = "cloudscript-multitarget-module-example"
