package devcli.issuetracker.domain

@JvmInline
value class WorkItemId private constructor(val value: Long) {
    companion object {
        fun of(raw: Long): WorkItemId {
            require(raw > 0) { "WorkItemId must be positive: $raw" }
            return WorkItemId(raw)
        }
        fun of(raw: String): WorkItemId = of(raw.trim().toLong())
    }
}

@JvmInline
value class WorkItemTitle private constructor(val value: String) {
    companion object {
        fun of(raw: String): WorkItemTitle {
            val trimmed = raw.trim()
            require(trimmed.isNotBlank()) { "WorkItemTitle cannot be blank" }
            return WorkItemTitle(trimmed)
        }
    }
}

@JvmInline
value class WorkItemBody private constructor(val value: String) {
    companion object {
        fun of(raw: String): WorkItemBody = WorkItemBody(raw.trim())
    }
}

enum class WorkItemType(val label: String) {
    FEATURE("type:feature"),
    BUG("type:bug"),
    TASK("type:task"),
    STORY("type:story");

    companion object {
        fun of(raw: String): WorkItemType {
            val normalized = raw.trim().lowercase().removePrefix("type:")
            return entries.firstOrNull { it.name.lowercase() == normalized }
                ?: throw IllegalArgumentException("Unknown WorkItemType: '$raw'. Valid values: ${entries.map { it.name.lowercase() }}")
        }
    }
}

enum class DeliveryPhase(val displayName: String) {
    DEFINE("01 Define"),
    SPEC("02 Spec"),
    PLAN("03 Plan"),
    EXECUTE("04 Execute"),
    REVIEW("05 Review"),
    SHIP("06 Ship"),
    IMPROVE("07 Improve");

    companion object {
        fun of(raw: String): DeliveryPhase {
            val cleaned = raw.trim().lowercase().replace("-", " ").replace("_", " ")
            return entries.firstOrNull {
                it.name.lowercase() == cleaned ||
                it.displayName.lowercase() == cleaned ||
                it.displayName.lowercase().removePrefix("01 ").removePrefix("02 ").removePrefix("03 ")
                    .removePrefix("04 ").removePrefix("05 ").removePrefix("06 ").removePrefix("07 ") == cleaned
            } ?: throw IllegalArgumentException("Unknown DeliveryPhase: '$raw'. Valid phases: ${entries.map { it.displayName }}")
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

class WorkItem(
    val id: WorkItemId,
    val title: WorkItemTitle,
    val body: WorkItemBody,
    val type: WorkItemType,
    val phase: DeliveryPhase,
    val url: String? = null
)
