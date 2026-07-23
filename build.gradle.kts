plugins {
    java
    `java-library`
    `maven-publish`
}

group = "net.codeverse"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
    // Published so that consumers get parameter names and documentation in
    // their editor. An API nobody can read from the IDE is an API nobody uses
    // correctly.
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(21)
    options.compilerArgs.add("-Xlint:all")
    options.compilerArgs.add("-parameters")
}

tasks.withType<Javadoc> {
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "codeverse-api"

            pom {
                name.set("Codeverse API")
                description.set("Shared contract for Codeverse network plugins: identity, trust tiers, "
                        + "voice restrictions, account linking and events")
                url.set("https://github.com/CodeVerseHub-Minecraft/CodeverseAPI")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                developers {
                    developer {
                        id.set("codeversehub-minecraft")
                        name.set("CodeVerseHub-Minecraft Subteam")
                    }
                }
            }
        }
    }
}
