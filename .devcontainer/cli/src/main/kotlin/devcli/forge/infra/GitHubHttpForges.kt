package devcli.forge.infra

import devcli.forge.domain.BranchName
import devcli.forge.domain.CommentBody
import devcli.forge.domain.Forges
import devcli.forge.domain.MergeStrategy
import devcli.forge.domain.PrBody
import devcli.forge.domain.PrTitle
import devcli.forge.domain.PullRequest
import devcli.forge.domain.PullRequestNumber
import devcli.forge.domain.RepositorySlug
import devcli.forge.domain.ReviewVerdict
import devcli.issuetracker.infra.GitHubGraphQLClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
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
}
