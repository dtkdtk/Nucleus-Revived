package io.github.nucleuspowered.gradle.task

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import io.github.nucleuspowered.gradle.enums.ReleaseLevel
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.ContentType
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.apache.http.impl.client.HttpClients
import org.apache.http.message.BasicNameValuePair
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.stream.Collectors

open class UploadToOre : DefaultTask() {

    @get:Input
    var multipartBoundary: String = "thisissomegibberishusedasaboundary"

    @get:Input
    var oreApiEndpoint: String = "https://ore.spongepowered.org/api"

    @get:Input
    var apiKey: String? = null

    @get:Input
    var force = false

    @get:Internal
    var fileProvider: Provider<RegularFile>? = null

    @get:Input
    var notes: () -> String = { "" }

    @get:Input
    var releaseLevel: () -> ReleaseLevel = { ReleaseLevel.SNAPSHOT }

    @get:Input
    var pluginid: String = ""

    @TaskAction
    fun checkAndUpload() {
        if (force || !releaseLevel.invoke().isSnapshot) {
            val conf = fileProvider
            val l = releaseLevel.invoke()
            if (apiKey != null && conf != null) {
                val authenticateUrl = URL("$oreApiEndpoint/v2/authenticate")
                val destroyUrl = URL("$oreApiEndpoint/v2/sessions/current")
                val uploadUri = URI("$oreApiEndpoint/v2/projects/$pluginid/versions")

                val fileToUpload = conf.get()
                this.logger.info("Starting upload")
                val con: HttpURLConnection = authenticateUrl.openConnection() as HttpURLConnection
                val gson = Gson()
                val key = try {
                    con.requestMethod = "POST"
                    con.setRequestProperty("Authorization", "OreApi apikey=$apiKey")
                    con.setRequestProperty("Content-Type", ContentType.APPLICATION_JSON.mimeType)
                    con.setRequestProperty("User-Agent", "Nucleus/Gradle")

                    val status = con.responseCode
                    if (status != 200) {
                        throw Exception("Error getting session: $status")
                    }

                    val response = returnStringFromInputStream(con.inputStream)
                    val json = gson.fromJson(response, JsonObject::class.java)
                    json["session"].asString
                } finally {
                    con.disconnect()
                }

                this.logger.info("Created session")

                val fileEntry = gson.toJson(FileUploadData(notes.invoke()))
                HttpClients.createDefault().use { httpClient ->
                    val post = HttpPost(uploadUri)
                    post.entity = MultipartEntityBuilder.create()
                        .addTextBody("plugin-info", fileEntry, ContentType.APPLICATION_JSON)
                        .addBinaryBody("plugin-file", fileToUpload.asFile)
                        .setBoundary(this.multipartBoundary)
                        .build()
                    post.addHeader("Authorization", "OreApi session=$key")
                    post.addHeader("Content-Type", ContentType.MULTIPART_FORM_DATA
                        .withParameters(
                            BasicNameValuePair("boundary", this.multipartBoundary)
                        ).toString())
                    post.addHeader("Accept", ContentType.APPLICATION_JSON.mimeType)
                    post.addHeader("User-Agent", "Nucleus/Gradle")
                    httpClient.execute(post).use {
                        val statusCode = it.statusLine.statusCode
                        if (statusCode != 201) {
                            destroySession(destroyUrl, key)
                            this.logger.error(
                                returnStringFromInputStream(it.entity.content)
                            )
                            throw GradleException("Failed to upload:\n" +
                                    "status code: $statusCode\n" +
                                    "status phrase: ${it.statusLine.reasonPhrase}")
                        }
                        val responseEntity = it.entity
                        val json = gson.fromJson(returnStringFromInputStream(responseEntity.content), JsonObject::class.java)
                        val fileInfoName = json["file_info"].asJsonObject["name"]
                        this.logger.info("Successfully uploaded: $fileInfoName")
                        this.logger.debug(json.toString())
                    }
                }

                destroySession(destroyUrl, key)
            }
        }
    }

    private fun destroySession(url: URL, key: String) {
        val postConnection: HttpURLConnection = url.openConnection() as HttpURLConnection
        try {
            postConnection.requestMethod = "DELETE"
            postConnection.setRequestProperty("Authorization", "OreApi session=$key")
            postConnection.setRequestProperty("User-Agent", "Nucleus/Gradle")
            val statusCodeDisconnection = postConnection.responseCode
            this.logger.info("Deleted session: $statusCodeDisconnection")
        } finally {
            postConnection.disconnect()
        }
    }

    private fun returnStringFromInputStream(inputStream: InputStream): String {
        return BufferedReader(InputStreamReader(inputStream)).use {
            it.lines().collect(Collectors.joining(System.lineSeparator()))
        }
    }
}

data class FileUploadData(
    @field:SerializedName("description") val description: String,
    @field:SerializedName("create_forum_post") val create_forum_post: Boolean = true
)