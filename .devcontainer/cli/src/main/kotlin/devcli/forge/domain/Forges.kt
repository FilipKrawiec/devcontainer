package devcli.forge.domain

interface Forges {
    fun createBranch(repo: RepositorySlug, branch: BranchName, base: BranchName)
    fun openPullRequest(repo: RepositorySlug, title: PrTitle, body: PrBody, head: BranchName, base: BranchName): PullRequest
    fun submitReview(repo: RepositorySlug, prNumber: PullRequestNumber, verdict: ReviewVerdict, body: CommentBody)
    fun merge(repo: RepositorySlug, prNumber: PullRequestNumber, strategy: MergeStrategy, deleteBranch: Boolean)

    fun listPipelineRuns(repo: RepositorySlug, branch: BranchName? = null, status: String? = null, limit: Int = 10): List<PipelineRun>
    fun getPipelineRun(repo: RepositorySlug, runId: PipelineRunId): PipelineRun
    fun getJobTrace(repo: RepositorySlug, jobId: JobId): JobTrace
}
