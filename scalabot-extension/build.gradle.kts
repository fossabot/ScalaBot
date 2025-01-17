plugins {
    kotlin("jvm")
    java
    `maven-publish`
    signing
}

dependencies {
    api("org.telegram:telegrambots-abilities:6.0.1")
    api("org.slf4j:slf4j-api:1.7.36")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.2")
    testImplementation("org.mockito:mockito-core:4.6.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

tasks.withType<Javadoc> {
    options {
        encoding = "UTF-8"
    }
}

java {
    withJavadocJar()
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    repositories {
        if (project.version.toString().endsWith("-SNAPSHOT")) {
            maven("https://nexus.kuku.me/repository/maven-snapshots/") {
                credentials {
                    username = project.properties["repo.credentials.private.username"].toString()
                    password = project.properties["repo.credentials.private.password"].toString()
                }
            }
        } else {
            maven("https://nexus.kuku.me/repository/maven-releases/") {
                credentials {
                    username = project.properties["repo.credentials.private.username"].toString()
                    password = project.properties["repo.credentials.private.password"].toString()
                }
            }
        }
    }

    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            pom {
                name.set("ScalaBot-Extension-api")
                description.set(
                    "Dependencies for developing scalabot " +
                            "(a robotic application based on the TelegramBots[Github@rubenlagus/TelegramBots] project)"
                )
                url.set("https://github.com/LamGC/ScalaBot")
                licenses {
                    license {
                        name.set("The MIT License")
                        url.set("https://www.opensource.org/licenses/mit-license.php")
                    }
                }
                developers {
                    developer {
                        id.set("LamGC")
                        name.set("LamGC")
                        email.set("lam827@lamgc.net")
                        url.set("https://github.com/LamGC")
                    }
                }
                scm {
                    connection.set("scm:git:https://github.com/LamGC/ScalaBot.git")
                    developerConnection.set("scm:git:https://github.com/LamGC/ScalaBot.git")
                    url.set("https://github.com/LamGC/ScalaBot")
                }
                issueManagement {
                    url.set("https://github.com/LamGC/ScalaBot/issues")
                    system.set("Github Issues")
                }
            }
        }
    }

}

signing {
    useGpgCmd()
    sign(publishing.publications["maven"])
}
