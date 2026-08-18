package devcli.forge.domain

@JvmInline
value class PullRequestNumber private constructor(val value: Long) {
    companion object {
        fun of(raw: Long): PullRequestNumber {
            require(raw > 0) { "PullRequestNumber must be positive: $raw" }
            return PullRequestNumber(raw)
        }
        fun of(raw: String): PullRequestNumber = of(raw.trim().toLong())
    }
}

@JvmInline
value class BranchName private constructor(val value: String) {
    companion object {
        fun of(raw: String): BranchName {
            val trimmed = raw.trim()
            require(trimmed.isNotBlank()) { "BranchName cannot be blank" }
            require(!trimmed.contains("..") && !trimmed.contains(" ") && !trimmed.contains("~") && !trimmed.contains("^")) {
                "Invalid branch name format: '$raw'"
            }
            return BranchName(trimmed)
        }
    }
}

@JvmInline
value class PrTitle private constructor(val value: String) {
    companion object {
        fun of(raw: String): PrTitle {
            val trimmed = raw.trim()
            require(trimmed.isNotBlank()) { "PrTitle cannot be blank" }
            return PrTitle(trimmed)
        }
    }
}

@JvmInline
value class PrBody private constructor(val value: String) {
    companion object {
        fun of(raw: String): PrBody = PrBody(raw.trim())
    }
}

enum class MergeStrategy(val flag: String) {
    SQUASH("--squash"),
    REBASE("--rebase"),
    MERGE("--merge");

    companion object {
        fun of(raw: String): MergeStrategy {
            val normalized = raw.trim().lowercase().removePrefix("--")
            return entries.firstOrNull { it.name.lowercase() == normalized }
                ?: throw IllegalArgumentException("Unknown MergeStrategy: '$raw'. Valid strategies: ${entries.map { it.name.lowercase() }}")
        }
    }
}

enum class ReviewVerdict(val apiAction: String) {
    APPROVE("APPROVE"),
    COMMENT("COMMENT"),
    REQUEST_CHANGES("REQUEST_CHANGES");

    companion object {
        fun of(raw: String): ReviewVerdict {
            val normalized = raw.trim().lowercase().replace("-", "_")
            return entries.firstOrNull { it.name.lowercase() == normalized }
                ?: throw IllegalArgumentException("Unknown ReviewVerdict: '$raw'. Valid verdicts: ${entries.map { it.name.lowercase().replace("_", "-") }}")
        }
    }
}

@JvmInline
value class RepositorySlug private constructor(val value: String) {
    val owner: String
        get() = value.substringBefore('/')
    val name: String
        get() = value.substringAfter('/')

    companion object {
        fun of(raw: String): RepositorySlug {
            val trimmed = raw.trim().removePrefix("https://github.com/").removeSuffix(".git")
            require(trimmed.contains('/')) { "RepositorySlug must be in format 'owner/repo', got '$raw'" }
            return RepositorySlug(trimmed)
        }
        fun of(owner: String, name: String): RepositorySlug = of("$owner/$name")
    }
}

@JvmInline
value class CommentBody private constructor(val value: String) {
    companion object {
        fun of(raw: String): CommentBody {
            val trimmed = raw.trim()
            require(trimmed.isNotBlank()) { "CommentBody cannot be blank" }
            return CommentBody(trimmed)
        }
    }
}

class PullRequest(
    val number: PullRequestNumber,
    val title: PrTitle,
    val body: PrBody,
    val head: BranchName,
    val base: BranchName,
    val url: String
)
