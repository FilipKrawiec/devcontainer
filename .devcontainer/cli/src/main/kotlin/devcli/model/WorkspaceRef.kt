package devcli.model

/**
 * Domain model representing a normalized Git remote or relative repository workspace path.
 */
data class WorkspaceRef(
    val rawRemote: String,
    val relativePath: String
) {
    val targetDirectoryPath: String
        get() = "/projects/${relativePath.trim('/')}"

    companion object {
        fun fromRemote(inputUrl: String): WorkspaceRef {
            var raw = inputUrl.trim().removeSuffix(".git")
            raw = when {
                raw.startsWith("ssh://git@") -> raw.removePrefix("ssh://git@").substringAfter('/')
                raw.startsWith("git@") && raw.contains(':') -> {
                    val host = raw.removePrefix("git@").substringBefore(':')
                    val path = raw.substringAfter(':')
                    "$host/$path"
                }
                raw.startsWith("https://") || raw.startsWith("http://") -> raw.substringAfter("://").substringAfter('/')
                else -> raw
            }
            val normalized = raw.trim('/')
            require(normalized.isNotBlank()) { "Invalid or empty repository remote URL" }
            return WorkspaceRef(rawRemote = inputUrl, relativePath = normalized)
        }
    }
}
