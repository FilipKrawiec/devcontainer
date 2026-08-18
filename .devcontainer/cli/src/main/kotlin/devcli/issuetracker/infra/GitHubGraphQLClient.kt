package devcli.issuetracker.infra

import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

object GitHubAuthTokenResolver {
    fun resolveToken(): String {
        System.getenv("GH_TOKEN")?.takeIf { it.isNotBlank() }?.let { return it }
        System.getenv("GITHUB_TOKEN")?.takeIf { it.isNotBlank() }?.let { return it }

        val userHome = System.getProperty("user.home") ?: "/home/vscode"
        val hostsFile = File("$userHome/.config/gh/hosts.yml")
        if (hostsFile.exists()) {
            for (line in hostsFile.readLines()) {
                val trimmed = line.trim()
                if (trimmed.startsWith("oauth_token:")) {
                    val token = trimmed.substringAfter("oauth_token:").trim()
                    if (token.isNotBlank()) return token
                }
            }
        }
        throw IllegalStateException("GitHub authentication token not found. Please set GH_TOKEN or run 'gh auth login'.")
    }
}

class GitHubGraphQLClient(
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build(),
    private val tokenProvider: () -> String = { GitHubAuthTokenResolver.resolveToken() },
    private val endpoint: URI = URI.create("https://api.github.com/graphql")
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun execute(query: String, variables: JsonObject = JsonObject(emptyMap())): JsonObject {
        val token = tokenProvider()
        val payload = buildJsonObject {
            put("query", query)
            put("variables", variables)
        }

        val request = HttpRequest.newBuilder(endpoint)
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .header("User-Agent", "dev-cli")
            .timeout(Duration.ofSeconds(15))
            .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw RuntimeException("GitHub GraphQL request failed with HTTP ${response.statusCode()}: ${response.body()}")
        }

        val responseJson = json.parseToJsonElement(response.body()).jsonObject
        if (responseJson.containsKey("errors")) {
            val errors = responseJson["errors"]
            throw RuntimeException("GitHub GraphQL error: $errors")
        }

        return responseJson["data"]?.jsonObject ?: JsonObject(emptyMap())
    }

    fun executeRest(
        url: String,
        method: String = "GET",
        body: String? = null
    ): HttpResponse<String> {
        val token = tokenProvider()
        val builder = HttpRequest.newBuilder(URI.create(url))
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "dev-cli")
            .timeout(Duration.ofSeconds(15))

        when (method.uppercase()) {
            "GET" -> builder.GET()
            "POST" -> builder.POST(HttpRequest.BodyPublishers.ofString(body ?: ""))
            "PUT" -> builder.PUT(HttpRequest.BodyPublishers.ofString(body ?: ""))
            "PATCH" -> builder.method("PATCH", HttpRequest.BodyPublishers.ofString(body ?: ""))
            "DELETE" -> builder.DELETE()
        }

        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }
}
