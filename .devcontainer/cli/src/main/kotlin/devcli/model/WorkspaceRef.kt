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
                    val host = after.substringBefore('/')
                    val path = after.substringAfter('/')
                    WorkspaceRef(remoteUrl = trimmed, relativePath = "$host/${path.trim('/')}")
                }
                clean.startsWith("git@") && clean.contains(':') -> {
                    val host = clean.removePrefix("git@").substringBefore(':')
                    val path = clean.substringAfter(':')
                    WorkspaceRef(remoteUrl = trimmed, relativePath = "$host/${path.trim('/')}")
                }
                clean.startsWith("https://") || clean.startsWith("http://") -> {
                    val scheme = clean.substringBefore("://")
                    val after = clean.substringAfter("://")
                    val host = after.substringBefore('/')
                    val path = after.substringAfter('/')
                    WorkspaceRef(remoteUrl = "$scheme://$host/${path.trim('/')}.git", relativePath = "$host/${path.trim('/')}")
                }
                clean.contains('/') -> {
                    val parts = clean.split('/').filter { it.isNotBlank() }
                    if (parts[0].contains('.')) {
                        // e.g. gitlab.com/group/project
                        val host = parts[0]
                        val path = parts.drop(1).joinToString("/")
                        WorkspaceRef(remoteUrl = "git@$host:$path.git", relativePath = "$host/$path")
                    } else {
                        // Short format e.g. FilipKrawiec/skills -> defaults to GitHub SSH
                        val path = parts.joinToString("/")
                        WorkspaceRef(remoteUrl = "git@github.com:$path.git", relativePath = "github.com/$path")
                    }
                }
                else -> {
                    throw IllegalArgumentException("Could not parse repository reference '$input'. Expected owner/repo or full Git remote URL.")
                }
            }
        }
    }
}
