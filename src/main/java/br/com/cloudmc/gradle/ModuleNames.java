package br.com.cloudmc.gradle;

final class ModuleNames {
    private ModuleNames() {
    }

    static String artifactName(String name, int apiVersion, String classifier) {
        return stripApiSuffix(name, apiVersion) + "-Api" + apiVersion + "-" + classifier + ".jar";
    }

    static String uploadFileName(String name, int apiVersion, String platform) {
        return stripApiSuffix(name, apiVersion)
            + "-Api" + apiVersion
            + ("cloudmc".equals(platform) ? "-cloudmc" : "")
            + ".jar";
    }

    private static String stripApiSuffix(String name, int apiVersion) {
        String result = name;
        String previous;
        do {
            previous = result;
            result = result.replaceFirst("(?i)(?:[-_]?api" + apiVersion + ")(?:[-_](?:desktop|cloudmc))?$", "");
        } while (!result.equals(previous));
        return result;
    }
}
