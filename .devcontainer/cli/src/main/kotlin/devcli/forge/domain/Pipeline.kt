package devcli.forge.domain

@JvmInline
value class PipelineRunId private constructor(val value: Long) {
    companion object {
        fun of(raw: Long): PipelineRunId {
            require(raw > 0) { "PipelineRunId must be positive: $raw" }
            return PipelineRunId(raw)
        }
        fun of(raw: String): PipelineRunId = of(raw.trim().toLong())
    }
}

@JvmInline
value class JobId private constructor(val value: Long) {
    companion object {
        fun of(raw: Long): JobId {
            require(raw > 0) { "JobId must be positive: $raw" }
            return JobId(raw)
        }
        fun of(raw: String): JobId = of(raw.trim().toLong())
    }
}

@JvmInline
value class WorkflowName private constructor(val value: String) {
    companion object {
        fun of(raw: String): WorkflowName {
            val trimmed = raw.trim()
            require(trimmed.isNotBlank()) { "WorkflowName cannot be blank" }
            return WorkflowName(trimmed)
        }
    }
}

enum class PipelineStatus(val label: String) {
    QUEUED("queued"),
    IN_PROGRESS("in_progress"),
    COMPLETED("completed"),
    SUCCESS("success"),
    FAILURE("failure"),
    CANCELLED("cancelled"),
    UNKNOWN("unknown");

    companion object {
        fun of(raw: String?): PipelineStatus {
            if (raw == null) return UNKNOWN
            val normalized = raw.trim().lowercase().replace("-", "_").replace(" ", "_")
            return entries.firstOrNull { it.name.lowercase() == normalized || it.label == normalized } ?: UNKNOWN
        }
    }
}

data class JobStep(
    val number: Int,
    val name: String,
    val status: String,
    val conclusion: String?
)

data class PipelineJob(
    val id: JobId,
    val name: String,
    val status: String,
    val conclusion: String?,
    val startedAt: String?,
    val completedAt: String?,
    val url: String,
    val steps: List<JobStep> = emptyList()
)

data class PipelineRun(
    val id: PipelineRunId,
    val workflow: WorkflowName,
    val branch: BranchName,
    val status: PipelineStatus,
    val conclusion: String?,
    val event: String,
    val commitSha: String,
    val createdAt: String,
    val url: String,
    val jobs: List<PipelineJob> = emptyList()
)

data class JobTrace(
    val jobId: JobId,
    val logContent: String
) {
    fun filter(pattern: String? = null, failedOnly: Boolean = false): List<String> {
        val lines = logContent.lines()
        val regex = pattern?.takeIf { it.isNotBlank() }?.toRegex(RegexOption.IGNORE_CASE)

        return lines.filter { line ->
            val matchesPattern = regex == null || regex.containsMatchIn(line)
            val matchesFailure = !failedOnly || isFailureLine(line)
            matchesPattern && matchesFailure
        }
    }

    private fun isFailureLine(line: String): Boolean {
        val lower = line.lowercase()
        return lower.contains("error") || lower.contains("fail") || lower.contains("fatal") || lower.contains("exception")
    }
}
