// Interfaces and value types only. Deliberately has no dependencies at all:
// anything added here lands on the classpath of every plugin that consumes the
// contract, whether they use it or not.
// Set on the base extension rather than the jar task, so the sources and
// javadoc archives are named consistently with the main one.
base {
    archivesName.set("codeverse-api")
}

publishing {
    publications.named<MavenPublication>("maven") {
        artifactId = "api"
        pom {
            name.set("Codeverse API")
            description.set("Shared contract for Codeverse network plugins: identity, trust tiers, "
                    + "voice restrictions, account linking and events")
        }
    }
}
