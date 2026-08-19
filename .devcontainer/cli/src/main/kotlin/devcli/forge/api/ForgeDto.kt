package devcli.forge.api

import devcli.forge.domain.JobStep
import devcli.forge.domain.PipelineJob
import devcli.forge.domain.PipelineRun
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
data class JobStepDto(
    val number: Int,
    val name: String,
    val status: String,
    val conclusion: String?
) {
    companion object {
        fun fromDomain(step: JobStep): JobStepDto = JobStepDto(
            number = step.number,
            name = step.name,
            status = step.status,
            conclusion = step.conclusion
        )
    }
}

@Serializable
data class PipelineJobDto(
    val id: Long,
    val name: String,
    val status: String,
    val conclusion: String?,
    val startedAt: String?,
    val completedAt: String?,
    val url: String,
    val steps: List<JobStepDto> = emptyList()
) {
    companion object {
        fun fromDomain(job: PipelineJob): PipelineJobDto = PipelineJobDto(
            id = job.id.value,
            name = job.name,
            status = job.status,
            conclusion = job.conclusion,
            startedAt = job.startedAt,
            completedAt = job.completedAt,
            url = job.url,
            steps = job.steps.map { JobStepDto.fromDomain(it) }
        )
    }
}

@Serializable
data class PipelineRunDto(
    val id: Long,
    val workflow: String,
    val branch: String,
    val status: String,
    val conclusion: String?,
    val event: String,
    val commitSha: String,
    val createdAt: String,
    val url: String,
    val jobs: List<PipelineJobDto> = emptyList()
) {
    companion object {
        fun fromDomain(run: PipelineRun): PipelineRunDto = PipelineRunDto(
            id = run.id.value,
            workflow = run.workflow.value,
            branch = run.branch.value,
            status = run.status.label,
            conclusion = run.conclusion,
            event = run.event,
            commitSha = run.commitSha,
            createdAt = run.createdAt,
            url = run.url,
            jobs = run.jobs.map { PipelineJobDto.fromDomain(it) }
        )
    }
}

@Serializable
data class JobTraceDto(
    val jobId: Long,
    val totalLines: Int,
    val matchedLines: Int,
    val lines: List<String>
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
        is PipelineRunDto -> json.encodeToString(dto)
        is JobTraceDto -> json.encodeToString(dto)
        is List<*> -> {
            @Suppress("UNCHECKED_CAST")
            if (dto.isNotEmpty() && dto.first() is PipelineRunDto) {
                json.encodeToString(dto as List<PipelineRunDto>)
            } else if (dto.isNotEmpty() && dto.first() is PipelineJobDto) {
                json.encodeToString(dto as List<PipelineJobDto>)
            } else {
                dto.toString()
            }
        }
        is ForgeErrorDto -> json.encodeToString(dto)
        else -> dto.toString()
    }
}
