plugins {
    java
    idea
    eclipse
    `maven-publish`
    id("net.kyori.blossom") version("1.3.1")
    id("nucleus-publishing-convention")
}

description = "The Ultimate Essentials Plugin API."

group = "io.github.nucleuspowered"

defaultTasks.add("licenseFormat")
defaultTasks.add("build")

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
}

repositories {
    mavenCentral()
    maven("https://repo.spongepowered.org/maven")
}

dependencies {
    implementation("org.spongepowered:spongeapi:" + rootProject.properties["spongeApiVersion"])
}

val filenameSuffix = "SpongeAPI${rootProject.properties["declaredApiVersion"]}"

val sourcesJar by tasks.registering(Jar::class) {
    dependsOn.add(JavaPlugin.CLASSES_TASK_NAME)
    archiveClassifier.set("sources")
    archiveFileName.set("Nucleus-${rootProject.version}-$filenameSuffix-API-sources.jar")
    from(sourceSets["main"].allSource)
}

val javadocJar by tasks.registering(Jar::class) {
    dependsOn.add(JavaPlugin.JAVADOC_TASK_NAME)
    archiveClassifier.set("javadoc")
    archiveFileName.set("Nucleus-${rootProject.version}-$filenameSuffix-API-javadocs.jar")
    from(tasks.javadoc)
}

val copyJars by tasks.registering(Copy::class) {
    dependsOn(tasks.jar)
    from(
            project.tasks.jar,
            javadocJar
    )
    into(rootProject.file("output"))
}

tasks {

    jar {
        manifest {
            attributes["API-Title"] = project.name
            attributes["Implementation-Title"] = rootProject.name
            attributes["API-Version"] = project.version
            attributes["Implementation-Version"] = rootProject.version
            attributes["Git-Hash"] = project.name

        }

        archiveVersion.set("${rootProject.version}")
        archiveFileName.set("Nucleus-${rootProject.version}-$filenameSuffix-API.jar")
    }

    build {
        dependsOn(javadocJar)
    }
}

publishing {
    publications {
        create<MavenPublication>("api") {
            from(components["java"])
            setArtifacts(listOf(javadocJar.get(), sourcesJar.get(), tasks.jar.get()))
            version = "${rootProject.version}"
            groupId = rootProject.properties["groupId"]?.toString()!!
            artifactId = project.properties["artifactId"]?.toString()!!
        }
    }
}

blossom {
    replaceTokenIn("src/main/java/io/github/nucleuspowered/nucleus/api/NucleusAPITokens.java")
    replaceToken("@version@", version)
    replaceToken("@description@", description)
    replaceToken("@gitHash@", rootProject.extra["gitHash"])
}

artifacts {
    archives(javadocJar)
    archives(sourcesJar)
    archives(tasks.jar)
}
