package devcli.model

/**
 * Domain model representing a normalized Git remote or relative repository workspace path.
 */
data class WorkspaceRef(
    val remoteUrl: String,
    val relativePath: String
) {
    val targetDirectoryPath: String
        get() = "/projects/${relativePath.trim('/')}"

    companion object {
        fun fromRemote(input: String): WorkspaceRef {
            val trimmed = input.trim()
            require(trimmed.isNotBlank()) { "Invalid or empty repository remote reference" }

            val clean = trimmed.removeSuffix(".git")

            return when {
                clean.startsWith("ssh://git@") -> {
                    val after = clean.removePrefix("ssh://git@")
                    require(after.contains('/')) { "Repository reference must include a repository path" }
                    val host = after.substringBefore('/')
                    val path = after.substringAfter('/')
                    workspaceRef(remoteUrl = trimmed, host = host, path = path)
                }
                clean.startsWith("git@") && clean.contains(':') -> {
                    val host = clean.removePrefix("git@").substringBefore(':')
                    val path = clean.substringAfter(':')
                    workspaceRef(remoteUrl = trimmed, host = host, path = path)
                }
                clean.startsWith("https://") || clean.startsWith("http://") -> {
                    val scheme = clean.substringBefore("://")
                    val after = clean.substringAfter("://")
                    require(after.contains('/')) { "Repository reference must include a repository path" }
                    val host = after.substringBefore('/')
                    val path = after.substringAfter('/')
                    workspaceRef(
                        remoteUrl = "$scheme://$host/${path.trim('/')}.git",
                        host = host,
                        path = path
                    )
                }
                clean.contains('/') -> {
                    val parts = clean.split('/').filter { it.isNotBlank() }
                    if (parts[0].contains('.')) {
                        // e.g. gitlab.com/group/project
                        val host = parts[0]
                        val path = parts.drop(1).joinToString("/")
                        workspaceRef(remoteUrl = "git@$host:$path.git", host = host, path = path)
                    } else {
                        // Short format e.g. FilipKrawiec/skills -> defaults to GitHub SSH
                        val path = parts.joinToString("/")
                        workspaceRef(remoteUrl = "git@github.com:$path.git", host = "github.com", path = path)
                    }
                }
                else -> {
                    throw IllegalArgumentException("Could not parse repository reference '$input'. Expected owner/repo or full Git remote URL.")
                }
            }
        }

        private fun workspaceRef(remoteUrl: String, host: String, path: String): WorkspaceRef {
            require(host.isNotBlank() && !host.contains('/') && !host.contains('\\')) {
                "Repository reference must include a valid Git host"
            }

            val normalizedPath = path.trim('/')
            val segments = normalizedPath.split('/')
            require(segments.isNotEmpty() && segments.all { segment ->
                segment.isNotBlank() &&
                    segment != "." &&
                    segment != ".." &&
                    !segment.contains('\\') &&
                    !segment.contains('?') &&
                    !segment.contains('#')
            }) {
                "Repository reference must contain a relative repository path without traversal segments"
            }

            return WorkspaceRef(remoteUrl = remoteUrl, relativePath = "$host/$normalizedPath")
        }
    }
}
