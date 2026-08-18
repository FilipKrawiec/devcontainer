package devcli.forge.api

import devcli.forge.domain.PullRequest
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class PullRequestDto(
    val number: Long,
    val title: String,
    val body: String,
    val head: String,
    val base: String,
    val url: String
) {
    companion object {
        fun fromDomain(pr: PullRequest): PullRequestDto = PullRequestDto(
            number = pr.number.value,
            title = pr.title.value,
            body = pr.body.value,
            head = pr.head.value,
            base = pr.base.value,
            url = pr.url
        )
    }
}

@Serializable
data class BranchResultDto(
    val branch: String,
    val base: String
)

@Serializable
data class ReviewResultDto(
    val prNumber: Long,
    val verdict: String,
    val notes: String
)

@Serializable
data class MergeResultDto(
    val prNumber: Long,
    val strategy: String,
    val branchDeleted: Boolean
)

@Serializable
data class ForgeErrorDto(
    val error: String
)

object ForgeJsonFormat {
    val json = Json { prettyPrint = true }

    fun toJson(dto: Any): String = when (dto) {
        is PullRequestDto -> json.encodeToString(dto)
        is BranchResultDto -> json.encodeToString(dto)
        is ReviewResultDto -> json.encodeToString(dto)
        is MergeResultDto -> json.encodeToString(dto)
        is ForgeErrorDto -> json.encodeToString(dto)
        else -> dto.toString()
    }
}
