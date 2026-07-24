plugins {
    java
    eclipse
    id("net.kyori.blossom") version("1.3.1")
    id("de.undercouch.download") version("5.7.0")
}

group = "io.github.nucleuspowered"

repositories {
    mavenCentral()
    maven("https://repo.spongepowered.org/maven")
    maven("https://repo.drnaylor.co.uk/artifactory/list/minecraft")
    maven("https://repo.drnaylor.co.uk/artifactory/list/quickstart")
    flatDir {
        dirs("../libs")
    }
    // maven("https://jitpack.io")
}

sourceSets {
    main {
        java {
            srcDir("src/main/java")
        }
        resources {
            srcDir("src/main/resources")
            exclude("assets/nucleus/suggestions/**")
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
}

tasks {
    processResources {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
}

// Dependency issues, skipping
tasks.withType<Test> {
    enabled = false
}

dependencies {
    annotationProcessor(project(":nucleus-ap"))
    implementation(project(":nucleus-ap"))
    implementation(project(":nucleus-api"))

    val dep = "org.spongepowered:spongeapi:" + rootProject.properties["spongeApiVersion"]
    annotationProcessor(dep)
    implementation(dep)

    implementation(rootProject.properties["qsmlDep"]?.toString()!!)
    implementation(rootProject.properties["neutrinoDep"]?.toString()!!) {
        exclude("org.spongepowered", "configurate-core")
    }

    testImplementation("org.mockito:mockito-all:1.10.19")
    testImplementation("org.powermock:powermock-module-junit4:1.6.4")
    testImplementation("org.powermock:powermock-api-mockito:1.6.4")
    testImplementation("org.hamcrest:hamcrest-junit:2.0.0.0")
    testImplementation("junit", "junit", "4.12")
}

val downloadCompat by tasks.registering(de.undercouch.gradle.tasks.download.Download::class) {
    src("https://nucleuspowered.org/data/nca.json")
    dest(File(buildDir, "resources/main/assets/nucleus/compat.json"))
    onlyIfModified(true)
}

blossom {
    replaceTokenIn("src/main/java/io/github/nucleuspowered/nucleus/NucleusPluginInfo.java")
    replaceToken("@name@", rootProject.name)
    replaceToken("@version@", version)

    replaceToken("@description@", rootProject.properties["description"])
    replaceToken("@url@", rootProject.properties["url"])
    replaceToken("@gitHash@", rootProject.extra["gitHash"])

    replaceToken("@spongeversion@", rootProject.properties["declaredApiVersion"]) //declaredApiVersion
}
