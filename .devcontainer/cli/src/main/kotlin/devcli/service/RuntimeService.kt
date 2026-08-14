package devcli.service

import java.io.File
import java.net.HttpURLConnection
import java.net.URI

data class RuntimeReport(
    val missingTools: List<String>,
    val projectsWritable: Boolean,
    val dockerSocketAvailable: Boolean,
    val previewSidecarAvailable: Boolean
)

class RuntimeService(
    private val requiredTools: List<String> = DEFAULT_REQUIRED_TOOLS,
    private val path: String = System.getenv("PATH").orEmpty(),
    private val projectsRoot: File = File("/projects"),
    private val dockerSocket: File = File("/var/run/docker.sock"),
    private val previewSidecarProbe: () -> Boolean = { isUrlReachable("http://host.docker.internal:8383/") }
) {
    fun inspect(): RuntimeReport = RuntimeReport(
        missingTools = requiredTools.filterNot(::isExecutableOnPath),
        projectsWritable = projectsRoot.isDirectory && projectsRoot.canWrite(),
        dockerSocketAvailable = dockerSocket.exists() && dockerSocket.canRead() && dockerSocket.canWrite(),
        previewSidecarAvailable = previewSidecarProbe()
    )

    private fun isExecutableOnPath(command: String): Boolean =
        path.split(File.pathSeparator)
            .filter { it.isNotBlank() }
            .any { directory -> File(directory, command).canExecute() }

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

        val DEFAULT_REQUIRED_TOOLS = listOf(
            "dev",
            "git",
            "node",
            "pnpm",
            "python",
            "java",
            "go",
            "rustc",
            "gradle",
            "flutter",
            "gh",
            "codex",
            "claude",
            "agy",
            "ollama"
        )
    }
}
