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

/** Epics sit on the epic board; every other issue sits on the issue board. */
enum class BoardKind(val displayName: String, val columns: List<BoardColumn>) {
    ISSUE("issue board", BoardColumn.entries),
    EPIC("epic board", listOf(BoardColumn.BACKLOG, BoardColumn.IN_PROGRESS, BoardColumn.DONE));

    fun allows(column: BoardColumn): Boolean = column in columns

    fun refusal(column: BoardColumn): String {
        val names = columns.map { it.displayName }
        return "The $displayName has only ${names.dropLast(1).joinToString(", ")} and ${names.last()}; got ${column.displayName}"
    }
}

enum class WorkItemType(val label: String, val board: BoardKind = BoardKind.ISSUE) {
    FEATURE("type:feature"),
    BUG("type:bug"),
    TASK("type:task"),
    STORY("type:story"),
    EPIC("type:epic", BoardKind.EPIC);

    companion object {
        fun of(raw: String): WorkItemType {
            val normalized = raw.trim().lowercase().removePrefix("type:")
            return entries.firstOrNull { it.name.lowercase() == normalized }
                ?: throw IllegalArgumentException("Unknown WorkItemType: '$raw'. Valid values: ${entries.map { it.name.lowercase() }}")
        }
    }
}

enum class BoardColumn(val displayName: String) {
    BACKLOG("Backlog"),
    TODO("Todo"),
    IN_PROGRESS("In progress"),
    REVIEW("Review"),
    DONE("Done");

    companion object {
        fun of(raw: String): BoardColumn {
            val cleaned = raw.trim().lowercase().replace("-", " ").replace("_", " ")
            return entries.firstOrNull { it.displayName.lowercase() == cleaned }
                ?: throw IllegalArgumentException("Unknown BoardColumn: '$raw'. Valid columns: ${entries.map { it.displayName }}")
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
    val column: BoardColumn?,
    val url: String? = null
)
