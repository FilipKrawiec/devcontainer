package devcli.service

import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.TimeUnit

enum class ToolCategory(val title: String) {
    CORE("Core & Git"),
    RUNTIMES("Runtimes & SDKs"),
    AI_AGENTS("AI Agents")
}

data class ToolDefinition(
    val name: String,
    val category: ToolCategory,
    val versionArgs: List<String> = listOf("--version")
)

data class ToolStatus(
    val name: String,
    val category: ToolCategory,
    val isInstalled: Boolean,
    val version: String? = null
)

data class RuntimeReport(
    val toolStatuses: List<ToolStatus>,
    val projectsWritable: Boolean,
    val projectsPath: String = "/projects",
    val dockerSocketAvailable: Boolean,
    val previewSidecarAvailable: Boolean
) {
    val missingTools: List<String>
        get() = toolStatuses.filterNot { it.isInstalled }.map { it.name }

    val allToolsInstalled: Boolean
        get() = missingTools.isEmpty()
}

open class RuntimeService(
    private val toolDefinitions: List<ToolDefinition> = DEFAULT_TOOL_DEFINITIONS,
    private val path: String = System.getenv("PATH").orEmpty(),
    private val projectsRoot: File = File("/projects"),
    private val dockerSocket: File = File("/var/run/docker.sock"),
    private val previewSidecarProbe: () -> Boolean = { isUrlReachable("http://host.docker.internal:8383/") },
    private val probeVersions: Boolean = true
) {
    // Secondary constructor for backwards compatibility with list of tool strings
    constructor(
        requiredTools: List<String>,
        path: String = System.getenv("PATH").orEmpty(),
        projectsRoot: File = File("/projects"),
        dockerSocket: File = File("/var/run/docker.sock"),
        previewSidecarProbe: () -> Boolean = { isUrlReachable("http://host.docker.internal:8383/") }
    ) : this(
        toolDefinitions = requiredTools.map { name ->
            DEFAULT_TOOL_DEFINITIONS.find { it.name == name }
                ?: ToolDefinition(name, ToolCategory.CORE)
        },
        path = path,
        projectsRoot = projectsRoot,
        dockerSocket = dockerSocket,
        previewSidecarProbe = previewSidecarProbe,
        probeVersions = false
    )

    open fun inspect(): RuntimeReport {
        val statuses = toolDefinitions.map { tool ->
            val installed = isExecutableOnPath(tool.name)
            val version = if (installed && probeVersions) probeToolVersion(tool) else null
            ToolStatus(
                name = tool.name,
                category = tool.category,
                isInstalled = installed,
                version = version
            )
        }

        return RuntimeReport(
            toolStatuses = statuses,
            projectsWritable = projectsRoot.isDirectory && projectsRoot.canWrite(),
            projectsPath = projectsRoot.path,
            dockerSocketAvailable = dockerSocket.exists() && dockerSocket.canRead() && dockerSocket.canWrite(),
            previewSidecarAvailable = previewSidecarProbe()
        )
    }

    private fun findExecutable(command: String): File? =
        path.split(File.pathSeparator)
            .filter { it.isNotBlank() }
            .map { directory -> File(directory, command) }
            .firstOrNull { it.canExecute() }

    private fun isExecutableOnPath(command: String): Boolean =
        findExecutable(command) != null

    private fun probeToolVersion(tool: ToolDefinition): String? {
        val exe = findExecutable(tool.name) ?: return null
        return try {
            val command = listOf(exe.absolutePath) + tool.versionArgs
            val pb = ProcessBuilder(command).redirectErrorStream(true)
            if (path.isNotBlank()) {
                pb.environment()["PATH"] = path
            }
            val process = pb.start()

            val finished = process.waitFor(1500, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                return null
            }

            if (process.exitValue() == 0) {
                val output = process.inputStream.bufferedReader().readText()
                cleanVersionOutput(tool.name, output)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun cleanVersionOutput(toolName: String, raw: String): String? {
        val lines = raw.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList()
        if (lines.isEmpty()) return null

        return when (toolName) {
            "node" -> lines.first()
            "pnpm" -> if (lines.first().startsWith("v")) lines.first() else "v${lines.first()}"
            "python" -> lines.first().removePrefix("Python").trim()
            "git" -> lines.first().removePrefix("git version").trim()
            "gh" -> lines.first().substringBefore("(").removePrefix("gh version").trim()
            "go" -> lines.first().removePrefix("go version").trim().substringBefore(" ").removePrefix("go").trim()
            "rustc" -> lines.first().removePrefix("rustc").substringBefore("(").trim()
            "flutter" -> lines.find { it.contains("Flutter", ignoreCase = true) }
                ?.substringBefore("•")?.removePrefix("Flutter")?.trim()
            "gradle" -> lines.find { it.startsWith("Gradle ") }?.removePrefix("Gradle ")?.trim()
            "java" -> {
                val line = lines.find { it.contains("version", ignoreCase = true) } ?: lines.first()
                val versionMatch = Regex("\"([^\"]+)\"").find(line)
                versionMatch?.groupValues?.get(1) ?: line.take(20)
            }
            "ollama" -> lines.first().removePrefix("ollama version is").trim()
            "agy" -> lines.first().removePrefix("agy").removePrefix("version").trim()
            "claude" -> lines.first().substringBefore("(").removePrefix("claude").removePrefix("version").trim()
            "codex" -> lines.first().removePrefix("codex-cli").removePrefix("codex").trim()
            "dev" -> lines.first().removePrefix("dev version").removePrefix("dev").removePrefix("version").trim()
            else -> lines.first().take(20)
        }
    }

    companion object {
        fun isUrlReachable(targetUrl: String): Boolean = try {
            val url = URI(targetUrl).toURL()
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 1000
            conn.readTimeout = 1000
            conn.requestMethod = "HEAD"
            conn.responseCode in 200..399
        } catch (_: Exception) {
            false
        }

        val DEFAULT_TOOL_DEFINITIONS = listOf(
            ToolDefinition("dev", ToolCategory.CORE, listOf("--version")),
            ToolDefinition("git", ToolCategory.CORE, listOf("--version")),
            ToolDefinition("gh", ToolCategory.CORE, listOf("--version")),

            ToolDefinition("node", ToolCategory.RUNTIMES, listOf("--version")),
            ToolDefinition("pnpm", ToolCategory.RUNTIMES, listOf("--version")),
            ToolDefinition("python", ToolCategory.RUNTIMES, listOf("--version")),
            ToolDefinition("java", ToolCategory.RUNTIMES, listOf("-version")),
            ToolDefinition("gradle", ToolCategory.RUNTIMES, listOf("--version")),
            ToolDefinition("go", ToolCategory.RUNTIMES, listOf("version")),
            ToolDefinition("rustc", ToolCategory.RUNTIMES, listOf("--version")),
            ToolDefinition("flutter", ToolCategory.RUNTIMES, listOf("--version")),

            ToolDefinition("agy", ToolCategory.AI_AGENTS, listOf("--version")),
            ToolDefinition("claude", ToolCategory.AI_AGENTS, listOf("--version")),
            ToolDefinition("codex", ToolCategory.AI_AGENTS, listOf("--version")),
            ToolDefinition("ollama", ToolCategory.AI_AGENTS, listOf("--version"))
        )

        val DEFAULT_REQUIRED_TOOLS = DEFAULT_TOOL_DEFINITIONS.map { it.name }
    }
}
