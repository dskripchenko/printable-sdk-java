plugins {
    `java-library`
    id("com.vanniktech.maven.publish") version "0.34.0"
}

group = "io.github.dskripchenko"
version = "1.1.0"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

repositories {
    mavenCentral()
}

dependencies {
    api("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    coordinates("io.github.dskripchenko", "printable-sdk", version.toString())

    pom {
        name.set("Printable SDK")
        description.set("Official Java SDK for the Printable document-generation service: HMAC-signed integration API client (templates, print-forms, batches, drafts, webhooks).")
        url.set("https://github.com/dskripchenko/printable-sdk-java")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
            }
        }
        developers {
            developer {
                id.set("dskripchenko")
                name.set("Denis Skripchenko")
                url.set("https://github.com/dskripchenko")
            }
        }
        scm {
            url.set("https://github.com/dskripchenko/printable-sdk-java")
            connection.set("scm:git:git://github.com/dskripchenko/printable-sdk-java.git")
            developerConnection.set("scm:git:ssh://git@github.com/dskripchenko/printable-sdk-java.git")
        }
    }
}
