package devcli.service

import java.io.File

data class RuntimeReport(
    val missingTools: List<String>,
    val projectsWritable: Boolean,
    val dockerSocketAvailable: Boolean
)

class RuntimeService(
    private val requiredTools: List<String> = DEFAULT_REQUIRED_TOOLS,
    private val path: String = System.getenv("PATH").orEmpty(),
    private val projectsRoot: File = File("/projects"),
    private val dockerSocket: File = File("/var/run/docker.sock")
) {
    fun inspect(): RuntimeReport = RuntimeReport(
        missingTools = requiredTools.filterNot(::isExecutableOnPath),
        projectsWritable = projectsRoot.isDirectory && projectsRoot.canWrite(),
        dockerSocketAvailable = dockerSocket.exists() && dockerSocket.canRead() && dockerSocket.canWrite()
    )

    private fun isExecutableOnPath(command: String): Boolean =
        path.split(File.pathSeparator)
            .filter { it.isNotBlank() }
            .any { directory -> File(directory, command).canExecute() }

    companion object {
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
