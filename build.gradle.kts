import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.7.10"
    id("com.github.johnrengelman.shadow") version "7.0.0"
    `maven-publish`
}

group = "co.pvphub"
version = "1.1"

repositories {
    mavenCentral()
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
}

sourceSets["main"].resources.srcDir("src/resources/")

dependencies {
    implementation(group = "com.github.kittinunf.fuel", name = "fuel", version = "2.3.1")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.electronwill.night-config:toml:3.6.6")
    compileOnly("com.velocitypowered:velocity-api:3.1.1")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}
tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
    archiveBaseName.set(project.name)
    mergeServiceFiles()
}
tasks {
    build {
        dependsOn(shadowJar)
    }
}
java {
    withJavadocJar()
    withSourcesJar()
}
publishing {
    repositories {
        maven {
            name = "pvphub-private"
            url = uri("https://maven.pvphub.me/private")
            credentials {
                username = System.getenv("PVPHUB_MAVEN_USERNAME")
                password = System.getenv("PVPHUB_MAVEN_SECRET")
            }
        }
    }
    publications {
        create<MavenPublication>(project.name) {
            from(components["java"])
        }
    }

}