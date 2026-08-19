package devcli.forge.infra

import devcli.forge.domain.BranchName
import devcli.forge.domain.CommentBody
import devcli.forge.domain.Forges
import devcli.forge.domain.JobId
import devcli.forge.domain.JobStep
import devcli.forge.domain.JobTrace
import devcli.forge.domain.MergeStrategy
import devcli.forge.domain.PipelineJob
import devcli.forge.domain.PipelineRun
import devcli.forge.domain.PipelineRunId
import devcli.forge.domain.PipelineStatus
import devcli.forge.domain.PrBody
import devcli.forge.domain.PrTitle
import devcli.forge.domain.PullRequest
import devcli.forge.domain.PullRequestNumber
import devcli.forge.domain.RepositorySlug
import devcli.forge.domain.ReviewVerdict
import devcli.forge.domain.WorkflowName
import devcli.issuetracker.infra.GitHubGraphQLClient
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

class GitHubHttpForges(
    private val client: GitHubGraphQLClient = GitHubGraphQLClient()
) : Forges {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override fun createBranch(repo: RepositorySlug, branch: BranchName, base: BranchName) {
        // 1. Get SHA of base branch
        val baseRefUrl = "https://api.github.com/repos/${repo.owner}/${repo.name}/git/ref/heads/${base.value}"
        val baseResponse = client.executeRest(baseRefUrl)
        if (baseResponse.statusCode() !in 200..299) {
            throw RuntimeException("Failed to find base branch '${base.value}': HTTP ${baseResponse.statusCode()} - ${baseResponse.body()}")
        }

        val baseJson = json.parseToJsonElement(baseResponse.body()).jsonObject
        val sha = baseJson["object"]?.jsonObject?.get("sha")?.jsonPrimitive?.content
            ?: throw RuntimeException("Base branch SHA missing in response")

        // 2. Create new branch ref
        val createRefUrl = "https://api.github.com/repos/${repo.owner}/${repo.name}/git/refs"
        val payload = buildJsonObject {
            put("ref", "refs/heads/${branch.value}")
            put("sha", sha)
        }

        val createResponse = client.executeRest(createRefUrl, "POST", payload.toString())
        if (createResponse.statusCode() !in 200..299 && createResponse.statusCode() != 422) {
            throw RuntimeException("Failed to create branch '${branch.value}': HTTP ${createResponse.statusCode()} - ${createResponse.body()}")
        }

        // Try local git checkout if git repo exists in current directory
        try {
            ProcessBuilder("git", "fetch", "origin").start().waitFor()
            ProcessBuilder("git", "checkout", branch.value).start().waitFor()
        } catch (_: Exception) {}
    }

    override fun openPullRequest(
        repo: RepositorySlug,
        title: PrTitle,
        body: PrBody,
        head: BranchName,
        base: BranchName
    ): PullRequest {
        val pullsUrl = "https://api.github.com/repos/${repo.owner}/${repo.name}/pulls"
        val payload = buildJsonObject {
            put("title", title.value)
            put("body", body.value)
            put("head", head.value)
            put("base", base.value)
        }

        val response = client.executeRest(pullsUrl, "POST", payload.toString())
        if (response.statusCode() !in 200..299) {
            throw RuntimeException("Failed to open pull request: HTTP ${response.statusCode()} - ${response.body()}")
        }

        val resJson = json.parseToJsonElement(response.body()).jsonObject
        val prNumber = resJson["number"]?.jsonPrimitive?.long ?: throw RuntimeException("PR number missing")
        val prUrl = resJson["html_url"]?.jsonPrimitive?.content ?: ""

        return PullRequest(
            number = PullRequestNumber.of(prNumber),
            title = title,
            body = body,
            head = head,
            base = base,
            url = prUrl
        )
    }

    override fun submitReview(
        repo: RepositorySlug,
        prNumber: PullRequestNumber,
        verdict: ReviewVerdict,
        body: CommentBody
    ) {
        val reviewUrl = "https://api.github.com/repos/${repo.owner}/${repo.name}/pulls/${prNumber.value}/reviews"
        val payload = buildJsonObject {
            put("event", verdict.apiAction)
            put("body", body.value)
        }

        val response = client.executeRest(reviewUrl, "POST", payload.toString())
        if (response.statusCode() !in 200..299) {
            throw RuntimeException("Failed to submit review: HTTP ${response.statusCode()} - ${response.body()}")
        }
    }

    override fun merge(
        repo: RepositorySlug,
        prNumber: PullRequestNumber,
        strategy: MergeStrategy,
        deleteBranch: Boolean
    ) {
        // 1. Get PR head branch details
        val prUrl = "https://api.github.com/repos/${repo.owner}/${repo.name}/pulls/${prNumber.value}"
        val prResponse = client.executeRest(prUrl)
        val headBranch = if (prResponse.statusCode() in 200..299) {
            val prJson = json.parseToJsonElement(prResponse.body()).jsonObject
            prJson["head"]?.jsonObject?.get("ref")?.jsonPrimitive?.content
        } else null

        // 2. Execute Merge
        val mergeUrl = "https://api.github.com/repos/${repo.owner}/${repo.name}/pulls/${prNumber.value}/merge"
        val payload = buildJsonObject {
            put("merge_method", strategy.name.lowercase())
        }

        val response = client.executeRest(mergeUrl, "PUT", payload.toString())
        if (response.statusCode() !in 200..299) {
            throw RuntimeException("Failed to merge PR #${prNumber.value}: HTTP ${response.statusCode()} - ${response.body()}")
        }

        // 3. Delete remote branch if requested
        if (deleteBranch && !headBranch.isNullOrBlank() && headBranch != "main" && headBranch != "master") {
            val deleteRefUrl = "https://api.github.com/repos/${repo.owner}/${repo.name}/git/refs/heads/$headBranch"
            client.executeRest(deleteRefUrl, "DELETE")

            try {
                ProcessBuilder("git", "checkout", "main").start().waitFor()
                ProcessBuilder("git", "pull", "origin", "main").start().waitFor()
                ProcessBuilder("git", "branch", "-D", headBranch).start().waitFor()
            } catch (_: Exception) {}
        }
    }

    override fun listPipelineRuns(
        repo: RepositorySlug,
        branch: BranchName?,
        status: String?,
        limit: Int
    ): List<PipelineRun> {
        val queryParams = mutableListOf("per_page=$limit")
        branch?.let { queryParams.add("branch=${URLEncoder.encode(it.value, StandardCharsets.UTF_8)}") }
        status?.takeIf { it.isNotBlank() }?.let { queryParams.add("status=${URLEncoder.encode(it, StandardCharsets.UTF_8)}") }

        val url = "https://api.github.com/repos/${repo.owner}/${repo.name}/actions/runs?${queryParams.joinToString("&")}"
        val response = client.executeRest(url)
        if (response.statusCode() !in 200..299) {
            throw RuntimeException("Failed to list pipeline runs: HTTP ${response.statusCode()} - ${response.body()}")
        }

        val resJson = json.parseToJsonElement(response.body()).jsonObject
        val runsArray = resJson["workflow_runs"]?.jsonArray ?: return emptyList()

        return runsArray.map { parsePipelineRun(it.jsonObject) }
    }

    override fun getPipelineRun(repo: RepositorySlug, runId: PipelineRunId): PipelineRun {
        val runUrl = "https://api.github.com/repos/${repo.owner}/${repo.name}/actions/runs/${runId.value}"
        val runResponse = client.executeRest(runUrl)
        if (runResponse.statusCode() !in 200..299) {
            throw RuntimeException("Failed to get pipeline run #${runId.value}: HTTP ${runResponse.statusCode()} - ${runResponse.body()}")
        }

        val runJson = json.parseToJsonElement(runResponse.body()).jsonObject
        val baseRun = parsePipelineRun(runJson)

        // Fetch jobs
        val jobsUrl = "https://api.github.com/repos/${repo.owner}/${repo.name}/actions/runs/${runId.value}/jobs"
        val jobsResponse = client.executeRest(jobsUrl)
        val jobs = if (jobsResponse.statusCode() in 200..299) {
            val jobsJson = json.parseToJsonElement(jobsResponse.body()).jsonObject
            val jobsArray = jobsJson["jobs"]?.jsonArray ?: emptyList()
            jobsArray.map { parsePipelineJob(it.jsonObject) }
        } else {
            emptyList()
        }

        return baseRun.copy(jobs = jobs)
    }

    override fun getJobTrace(repo: RepositorySlug, jobId: JobId): JobTrace {
        val logUrl = "https://api.github.com/repos/${repo.owner}/${repo.name}/actions/jobs/${jobId.value}/logs"
        val response = client.executeRest(logUrl)
        if (response.statusCode() !in 200..299) {
            throw RuntimeException("Failed to get job trace for job #${jobId.value}: HTTP ${response.statusCode()} - ${response.body()}")
        }

        return JobTrace(jobId = jobId, logContent = response.body())
    }

    private fun parsePipelineRun(obj: JsonObject): PipelineRun {
        val id = obj["id"]?.jsonPrimitive?.long ?: 0L
        val name = obj["name"]?.jsonPrimitive?.content ?: "Workflow"
        val headBranch = obj["head_branch"]?.jsonPrimitive?.content ?: "main"
        val statusRaw = obj["status"]?.jsonPrimitive?.content ?: "unknown"
        val conclusion = obj["conclusion"]?.jsonPrimitive?.content
        val event = obj["event"]?.jsonPrimitive?.content ?: "push"
        val headSha = obj["head_sha"]?.jsonPrimitive?.content ?: ""
        val createdAt = obj["created_at"]?.jsonPrimitive?.content ?: ""
        val htmlUrl = obj["html_url"]?.jsonPrimitive?.content ?: ""

        val effectiveStatus = if (statusRaw == "completed" && conclusion != null) {
            PipelineStatus.of(conclusion)
        } else {
            PipelineStatus.of(statusRaw)
        }

        return PipelineRun(
            id = PipelineRunId.of(id),
            workflow = WorkflowName.of(name),
            branch = BranchName.of(if (headBranch.isNotBlank()) headBranch else "main"),
            status = effectiveStatus,
            conclusion = conclusion,
            event = event,
            commitSha = headSha,
            createdAt = createdAt,
            url = htmlUrl
        )
    }

    private fun parsePipelineJob(obj: JsonObject): PipelineJob {
        val id = obj["id"]?.jsonPrimitive?.long ?: 0L
        val name = obj["name"]?.jsonPrimitive?.content ?: "Job"
        val status = obj["status"]?.jsonPrimitive?.content ?: "unknown"
        val conclusion = obj["conclusion"]?.jsonPrimitive?.content
        val startedAt = obj["started_at"]?.jsonPrimitive?.content
        val completedAt = obj["completed_at"]?.jsonPrimitive?.content
        val htmlUrl = obj["html_url"]?.jsonPrimitive?.content ?: ""

        val stepsArray = obj["steps"]?.jsonArray ?: emptyList()
        val steps = stepsArray.map { stepElem ->
            val sObj = stepElem.jsonObject
            JobStep(
                number = sObj["number"]?.jsonPrimitive?.intOrNull ?: 0,
                name = sObj["name"]?.jsonPrimitive?.content ?: "",
                status = sObj["status"]?.jsonPrimitive?.content ?: "",
                conclusion = sObj["conclusion"]?.jsonPrimitive?.content
            )
        }

        return PipelineJob(
            id = JobId.of(id),
            name = name,
            status = status,
            conclusion = conclusion,
            startedAt = startedAt,
            completedAt = completedAt,
            url = htmlUrl,
            steps = steps
        )
    }
}
