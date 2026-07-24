package io.github.nucleuspowered.gradle.task

import io.github.nucleuspowered.gradle.enums.ReleaseLevel
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import javax.inject.Inject

open class RelNotesTask @Inject constructor() : DefaultTask() {

    @get:Internal
    var relNotes: String? = null
        private set

    @get:Input
    private var versionStringProvider: () -> String = { -> "" }

    @get:Input
    private var gitHashProvider: () -> String = { -> "" }

    @get:Input
    private var gitCommitProvider: () -> String = { -> "" }

    @get:Input
    private var releaseLevelProvider: () -> ReleaseLevel = { -> ReleaseLevel.SNAPSHOT }

    fun versionString(provider: () -> String) {
        this.versionStringProvider = provider
    }

    fun gitHash(provider: () -> String) {
        this.gitHashProvider = provider
    }

    fun gitCommit(provider: () -> String) {
        this.gitCommitProvider = provider
    }

    fun level(provider: () -> ReleaseLevel) {
        this.releaseLevelProvider = provider
    }

    @TaskAction
    fun doTask() {
        val versionString = this.versionStringProvider.invoke()
        val level = this.releaseLevelProvider.invoke()
        val templatePath = project.projectDir.toPath()
            .resolve("changelogs")
            .resolve("templates")
            .resolve("${level.template}.md")

        val notesDir = project.projectDir.toPath()
            .resolve("changelogs")
            .resolve("templates")

        val notesFull = notesDir.resolve("$versionString.md")
        val notes = if (Files.exists(notesFull)) {
            notesFull
        } else {
            notesDir.resolve("${versionString.substringBefore("-")}.md")
        }

        val templateText: String = if (Files.exists(templatePath)) {
            String(Files.readAllBytes(templatePath), StandardCharsets.UTF_8)
        } else {
            "There are no templated release notes available."
        }

        val notesText: String = if (Files.exists(notes)) {
            String(Files.readAllBytes(notes), StandardCharsets.UTF_8)
        } else {
            "There are no release notes available."
        }

        relNotes = templateText
            .replace("{{hash}}", this.gitHashProvider.invoke())
            .replace("{{info}}", notesText)
            .replace("{{version}}", project.properties["nucleusVersion"]?.toString()!!)
            .replace("{{message}}", this.gitCommitProvider.invoke())
            .replace("{{sponge}}", project.properties["declaredApiVersion"]?.toString()!!)
    }
}