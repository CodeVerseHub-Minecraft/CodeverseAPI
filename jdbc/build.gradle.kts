// A reference implementation of IdentityService that reads the shared database
// directly. Lives here rather than inside a plugin because every consumer needs
// it: a backend has no authentication plugin to ask, and an implementation
// buried in one plugin cannot be reached by another.
dependencies {
    api(project(":api"))
    implementation("com.github.ben-manes.caffeine:caffeine:3.2.4")
}

// Set on the base extension rather than the jar task, so the sources and
// javadoc archives are named consistently with the main one.
base {
    archivesName.set("codeverse-api-jdbc")
}

publishing {
    publications.named<MavenPublication>("maven") {
        artifactId = "jdbc"
        pom {
            name.set("Codeverse API JDBC")
            description.set("Database backed IdentityService, so servers without the authentication "
                    + "plugin can still resolve network identities")
        }
    }
}
