package devcli.issuetracker.api

import devcli.issuetracker.domain.WorkItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class WorkItemDto(
    val id: Long,
    val title: String,
    val body: String,
    val type: String,
    val phase: String,
    val url: String? = null
) {
    companion object {
        fun fromDomain(item: WorkItem): WorkItemDto = WorkItemDto(
            id = item.id.value,
            title = item.title.value,
            body = item.body.value,
            type = item.type.name.lowercase(),
            phase = item.phase.displayName,
            url = item.url
        )
    }
}

@Serializable
data class CommentResponseDto(
    val issueId: Long,
    val commentUrl: String
)

@Serializable
data class ErrorDto(
    val error: String
)

object JsonFormat {
    val json = Json { prettyPrint = true }

    fun toJson(dto: Any): String = when (dto) {
        is WorkItemDto -> json.encodeToString(dto)
        is CommentResponseDto -> json.encodeToString(dto)
        is ErrorDto -> json.encodeToString(dto)
        else -> dto.toString()
    }
}
