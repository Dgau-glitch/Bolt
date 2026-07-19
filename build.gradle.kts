import java.io.ByteArrayOutputStream

plugins {
    id("java-library")
    id("maven-publish")
    id("io.github.goooler.shadow") version "8.1.7"
}

subprojects {
    plugins.apply("java-library")
    plugins.apply("maven-publish")
    plugins.apply("io.github.goooler.shadow")

    group = "${project.property("group")}"
    version = "${project.property("version")}.${commitsSinceLastTag()}"

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
        withSourcesJar()
    }

    tasks {
        withType<JavaCompile> {
            options.encoding = "UTF-8"
        }
        jar {
            archiveClassifier.set("noshade")
        }
        shadowJar {
            archiveClassifier.set("")
            archiveFileName.set("${project.property("artifactName")}-${project.version}.jar")
        }
        build {
            dependsOn(shadowJar)
        }
    }

    publishing {
        repositories {
            if (project.hasProperty("mavenUsername") && project.hasProperty("mavenPassword")) {
                maven {
                    credentials {
                        username = "${project.property("mavenUsername")}"
                        password = "${project.property("mavenPassword")}"
                    }
                    url = uri("https://repo.codemc.io/repository/maven-releases/")
                }
            }
        }
        publications {
            create<MavenPublication>("maven") {
                groupId = "${project.group}"
                artifactId = project.name
                version = "${project.version}"
                from(components["java"])
            }
        }
    }
}

fun commitsSinceLastTag(): String {
    val tagDescription = ByteArrayOutputStream()
    exec {
        commandLine("git", "describe", "--tags", "--always")
        standardOutput = tagDescription
    }
    if (tagDescription.toString().indexOf('-') < 0) {
        return "0"
    }
    return tagDescription.toString().split('-')[1]
}

tasks.register("checkFoliaMigration") {
    group = "verification"
    description = "Checks for Folia migration regressions in scheduler and entity lookup hot paths."
    doLast {
        val sourceRoots = listOf("bukkit/src/main/java", "folia/src/main/java")
        val schedulerAdapter = file("folia/src/main/java/org/popcraft/bolt/util/DefaultFoliaSchedulerService.java").canonicalFile
        val inventoryListener = file("bukkit/src/main/java/org/popcraft/bolt/listeners/InventoryListener.java").canonicalFile
        val listenerRoots = listOf(
            file("bukkit/src/main/java/org/popcraft/bolt/listeners").toPath(),
            file("folia/src/main/java/org/popcraft/bolt/listeners").toPath()
        )
        val forbiddenScheduler = Regex("Bukkit\\.getScheduler|getServer\\(\\)\\.getScheduler|scheduleSync")
        val forbiddenChunkEntities = Regex("\\.getEntities\\(")
        val forbiddenInventoryExtendedLookup = Regex("\\.findProtection\\(")
        val forbiddenBlockingJoin = Regex("\\.join\\(")
        val violations = mutableListOf<String>()
        sourceRoots.map(::file).filter(File::exists).forEach { root ->
            root.walkTopDown().filter { it.isFile && it.extension == "java" }.forEach { source ->
                val canonicalSource = source.canonicalFile
                val text = source.readText()
                if (canonicalSource != schedulerAdapter && forbiddenScheduler.containsMatchIn(text)) {
                    violations.add("Forbidden scheduler API outside adapter: ${source.relativeTo(projectDir)}")
                }
                if (forbiddenChunkEntities.containsMatchIn(text)) {
                    violations.add("Forbidden Chunk#getEntities-style scan: ${source.relativeTo(projectDir)}")
                }
                if (canonicalSource == inventoryListener && forbiddenInventoryExtendedLookup.containsMatchIn(text)) {
                    violations.add("Inventory hot path must use direct protection lookup, not extended matchers: ${source.relativeTo(projectDir)}")
                }
                if (listenerRoots.any { source.toPath().startsWith(it) } && forbiddenBlockingJoin.containsMatchIn(text)) {
                    violations.add("Listeners must not block region/entity threads with CompletableFuture.join(): ${source.relativeTo(projectDir)}")
                }
            }
        }
        if (violations.isNotEmpty()) {
            throw GradleException(violations.joinToString(System.lineSeparator()))
        }
    }
}

tasks.named("check") {
    dependsOn("checkFoliaMigration")
}

// The root project is an aggregator. Do not publish an empty root jar that can be mistaken for the runtime plugin.
tasks.named<Jar>("jar") {
    enabled = false
}

tasks.named("shadowJar") {
    enabled = false
}

tasks.named("assemble") {
    dependsOn(":bolt-bukkit:shadowJar")
}

tasks.register("verifyPluginJarMetadata") {
    group = "verification"
    description = "Verifies that the runtime plugin jar contains Bukkit/Folia plugin metadata."
    dependsOn(":bolt-bukkit:shadowJar")
    doLast {
        val shadowJar = project(":bolt-bukkit").tasks.named("shadowJar").get()
        val archiveFile = (shadowJar as AbstractArchiveTask).archiveFile.get().asFile
        if (!archiveFile.isFile) {
            throw GradleException("Runtime plugin jar was not created: $archiveFile")
        }
        val entries = zipTree(archiveFile).matching {
            include("plugin.yml", "paper-plugin.yml")
        }.files
        if (entries.isEmpty()) {
            throw GradleException("Runtime plugin jar is missing plugin.yml or paper-plugin.yml: $archiveFile")
        }
    }
}

tasks.named("check") {
    dependsOn("verifyPluginJarMetadata")
}
