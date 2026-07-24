package io.github.nucleuspowered.gradle.task

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.security.DigestInputStream
import java.security.MessageDigest

open class SetupServer : DefaultTask() {

    @get:Input
    var directory: String = "run"

    @get:Input
    var spongeVanillaFileName: String = "sv.jar"

    @get:Input
    var spongeVanillaDownload: URL? = null

    @get:Input
    var spongeVanillaSHA1Hash: String? = null

    @get:Internal
    var fileProvider: Provider<RegularFile>? = null

    @get:Input
    var acceptEula: Boolean = false

    @TaskAction
    fun execute() {
        if (this.spongeVanillaDownload == null || this.spongeVanillaSHA1Hash == null) {
            throw GradleException("The file and SHA1 hash must be set.")
        }
        if (!this.acceptEula) {
            throw GradleException("The EULA must be accepted to run this task")
        }
        if (fileProvider?.isPresent != true) {
            throw GradleException("The plugin was not specified!")
        }

        val file: RegularFile = fileProvider!!.get()
        val pathForServer = Paths.get(directory)
        val svdl = this.spongeVanillaDownload!!

        if (Files.exists(pathForServer) && !Files.isDirectory(pathForServer)) {
            throw GradleException("The specified path is not a directory!")
        }

        Files.createDirectories(pathForServer)
        val svFile = pathForServer.resolve(spongeVanillaFileName)
        if (Files.notExists(svFile)) {
            logger.quiet("Downloading SpongeVanilla")
            val con: HttpURLConnection = svdl.openConnection() as HttpURLConnection
            try {
                con.requestMethod = "GET"
                con.doInput = true
                con.setRequestProperty("User-Agent", "Nucleus/Gradle")

                val status = con.responseCode
                if (status != 200) {
                    throw Exception("Error getting file: $status")
                }

                Files.newOutputStream(svFile, StandardOpenOption.CREATE).use {
                    it.write(con.inputStream.readBytes())
                }
                logger.quiet("File downloaded")
            } catch (ex: Exception) {
                throw GradleException("Could not download", ex)
            } finally {
                con.disconnect()
            }

            logger.quiet("Checking Hash for SV")
            val hash = checkHashOfFile(svFile)
            if (hash != spongeVanillaSHA1Hash!!) {
                Files.deleteIfExists(svFile)
                throw GradleException("Expected hash $spongeVanillaSHA1Hash does not match actual hash $hash. File has been deleted.")
            }

            logger.quiet("Creating Server")
        }

        val modsDir = pathForServer.resolve("mods")
        val nucleusFile = modsDir.resolve("nucleus.jar")
        Files.createDirectories(modsDir)
        Files.deleteIfExists(nucleusFile)
        Files.createDirectories(pathForServer.resolve("docs"))
        Files.copy(file.asFile.toPath(), nucleusFile)

        val eulaFile = pathForServer.resolve("eula.txt")
        if (Files.notExists(eulaFile)) {
            Files.newBufferedWriter(eulaFile, StandardOpenOption.CREATE_NEW).use {
                it.write("eula=true")
            }
        }
    }

    private fun byteToHex(num: Int): String {
        val hexDigits = CharArray(2)
        hexDigits[0] = Character.forDigit(num shr 4 and 0xF, 16)
        hexDigits[1] = Character.forDigit(num and 0xF, 16)
        return String(hexDigits)
    }

    private fun checkHashOfFile(svFile: Path): String {
        val digest: MessageDigest = MessageDigest.getInstance("SHA-1")
        DigestInputStream(Files.newInputStream(svFile), digest).use {
            while (it.read() != -1) { /* ignored */ }
            return it.messageDigest.digest().joinToString("") { i -> byteToHex(i.toInt()) }
        }
    }
}