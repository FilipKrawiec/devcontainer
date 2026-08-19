package devcli.forge

import devcli.forge.api.ForgeJsonFormat
import devcli.forge.api.JobTraceDto
import devcli.forge.api.MergeResultDto
import devcli.forge.api.PipelineJobDto
import devcli.forge.api.PipelineRunDto
import devcli.forge.api.PullRequestDto
import devcli.forge.api.ReviewResultDto
import devcli.forge.app.CreateBranchUseCase
import devcli.forge.app.GetJobTraceUseCase
import devcli.forge.app.GetPipelineRunUseCase
import devcli.forge.app.ListPipelineRunsUseCase
import devcli.forge.app.MergePullRequestUseCase
import devcli.forge.app.OpenPullRequestUseCase
import devcli.forge.app.ReviewPullRequestUseCase
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class InMemoryForges : Forges {
    val branches = mutableMapOf<String, MutableSet<String>>()
    val pullRequests = mutableMapOf<Long, PullRequest>()
    val reviews = mutableListOf<Pair<Long, Pair<ReviewVerdict, String>>>()
    val merged = mutableListOf<Long>()
    val pipelineRuns = mutableMapOf<Long, PipelineRun>()
    val jobTraces = mutableMapOf<Long, String>()
    private var prSeq = 1L

    override fun createBranch(repo: RepositorySlug, branch: BranchName, base: BranchName) {
        val repoBranches = branches.getOrPut(repo.value) { mutableSetOf("main") }
        require(repoBranches.contains(base.value)) { "Base branch '${base.value}' does not exist" }
        repoBranches.add(branch.value)
    }

    override fun openPullRequest(
        repo: RepositorySlug,
        title: PrTitle,
        body: PrBody,
        head: BranchName,
        base: BranchName
    ): PullRequest {
        val id = PullRequestNumber.of(prSeq++)
        val pr = PullRequest(
            number = id,
            title = title,
            body = body,
            head = head,
            base = base,
            url = "https://github.com/${repo.value}/pull/${id.value}"
        )
        pullRequests[id.value] = pr
        return pr
    }

    override fun submitReview(
        repo: RepositorySlug,
        prNumber: PullRequestNumber,
        verdict: ReviewVerdict,
        body: CommentBody
    ) {
        require(pullRequests.containsKey(prNumber.value)) { "PR #${prNumber.value} not found" }
        reviews.add(prNumber.value to (verdict to body.value))
    }

    override fun merge(
        repo: RepositorySlug,
        prNumber: PullRequestNumber,
        strategy: MergeStrategy,
        deleteBranch: Boolean
    ) {
        val pr = pullRequests[prNumber.value] ?: throw NoSuchElementException("PR #${prNumber.value} not found")
        merged.add(pr.number.value)
        if (deleteBranch) {
            branches[repo.value]?.remove(pr.head.value)
        }
    }

    override fun listPipelineRuns(
        repo: RepositorySlug,
        branch: BranchName?,
        status: String?,
        limit: Int
    ): List<PipelineRun> {
        return pipelineRuns.values
            .filter { branch == null || it.branch.value == branch.value }
            .filter { status == null || it.status.label.equals(status, ignoreCase = true) }
            .take(limit)
    }

    override fun getPipelineRun(repo: RepositorySlug, runId: PipelineRunId): PipelineRun {
        return pipelineRuns[runId.value] ?: throw NoSuchElementException("Pipeline run #${runId.value} not found")
    }

    override fun getJobTrace(repo: RepositorySlug, jobId: JobId): JobTrace {
        val log = jobTraces[jobId.value] ?: throw NoSuchElementException("Job trace #${jobId.value} not found")
        return JobTrace(jobId, log)
    }
}

class ForgeTest {

    @Test
    fun `BranchName validates and trims ref format`() {
        assertEquals("issue-12-test", BranchName.of("issue-12-test").value)
        assertEquals("feature/new-api", BranchName.of("feature/new-api").value)
        assertFailsWith<IllegalArgumentException> { BranchName.of("   ") }
        assertFailsWith<IllegalArgumentException> { BranchName.of("invalid..branch") }
    }

    @Test
    fun `PullRequestNumber enforces positive integer`() {
        assertEquals(11L, PullRequestNumber.of(11).value)
        assertEquals(5L, PullRequestNumber.of("5").value)
        assertFailsWith<IllegalArgumentException> { PullRequestNumber.of(0) }
        assertFailsWith<IllegalArgumentException> { PullRequestNumber.of(-1) }
    }

    @Test
    fun `MergeStrategy parses supported strategies`() {
        assertEquals(MergeStrategy.SQUASH, MergeStrategy.of("squash"))
        assertEquals(MergeStrategy.REBASE, MergeStrategy.of("rebase"))
        assertEquals(MergeStrategy.MERGE, MergeStrategy.of("merge"))
        assertFailsWith<IllegalArgumentException> { MergeStrategy.of("fastforward") }
    }

    @Test
    fun `ReviewVerdict parses review decisions`() {
        assertEquals(ReviewVerdict.APPROVE, ReviewVerdict.of("approve"))
        assertEquals(ReviewVerdict.COMMENT, ReviewVerdict.of("comment"))
        assertEquals(ReviewVerdict.REQUEST_CHANGES, ReviewVerdict.of("request-changes"))
        assertFailsWith<IllegalArgumentException> { ReviewVerdict.of("unknown") }
    }

    @Test
    fun `CreateBranchUseCase creates branch off base`() {
        val forges = InMemoryForges()
        val useCase = CreateBranchUseCase(forges)
        val repo = RepositorySlug.of("FilipKrawiec/devcontainer")

        val outcome = useCase.execute(repo, BranchName.of("issue-12-work"), BranchName.of("main"))
        assertIs<CreateBranchUseCase.Outcome.Success>(outcome)
        assertEquals("issue-12-work", outcome.branch.value)
        assertTrue(forges.branches[repo.value]?.contains("issue-12-work") == true)
    }

    @Test
    fun `OpenPullRequestUseCase opens PR successfully`() {
        val forges = InMemoryForges()
        val useCase = OpenPullRequestUseCase(forges)
        val repo = RepositorySlug.of("FilipKrawiec/devcontainer")

        val outcome = useCase.execute(
            repo,
            PrTitle.of("fix(cli): resolve bug"),
            PrBody.of("Closes #10"),
            BranchName.of("issue-10-fix"),
            BranchName.of("main")
        )
        assertIs<OpenPullRequestUseCase.Outcome.Success>(outcome)
        assertEquals(1L, outcome.pullRequest.number.value)
        assertEquals("fix(cli): resolve bug", outcome.pullRequest.title.value)
        assertTrue(outcome.pullRequest.url.contains("/pull/1"))
    }

    @Test
    fun `ReviewPullRequestUseCase submits verdict and notes`() {
        val forges = InMemoryForges()
        val openUseCase = OpenPullRequestUseCase(forges)
        val reviewUseCase = ReviewPullRequestUseCase(forges)
        val repo = RepositorySlug.of("FilipKrawiec/devcontainer")

        val pr = (openUseCase.execute(repo, PrTitle.of("Test PR"), PrBody.of(""), BranchName.of("feat"), BranchName.of("main")) as OpenPullRequestUseCase.Outcome.Success).pullRequest

        val outcome = reviewUseCase.execute(repo, pr.number, ReviewVerdict.APPROVE, CommentBody.of("LGTM - Quality Engineer verified"))
        assertIs<ReviewPullRequestUseCase.Outcome.Success>(outcome)
        assertEquals(1, forges.reviews.size)
        assertEquals(ReviewVerdict.APPROVE, forges.reviews.first().second.first)
    }

    @Test
    fun `MergePullRequestUseCase merges and deletes branch`() {
        val forges = InMemoryForges()
        val branchUseCase = CreateBranchUseCase(forges)
        val openUseCase = OpenPullRequestUseCase(forges)
        val mergeUseCase = MergePullRequestUseCase(forges)
        val repo = RepositorySlug.of("FilipKrawiec/devcontainer")

        branchUseCase.execute(repo, BranchName.of("feat-branch"), BranchName.of("main"))
        val pr = (openUseCase.execute(repo, PrTitle.of("PR"), PrBody.of(""), BranchName.of("feat-branch"), BranchName.of("main")) as OpenPullRequestUseCase.Outcome.Success).pullRequest

        val outcome = mergeUseCase.execute(repo, pr.number, MergeStrategy.SQUASH, deleteBranch = true)
        assertIs<MergePullRequestUseCase.Outcome.Success>(outcome)
        assertTrue(forges.merged.contains(pr.number.value))
        assertTrue(forges.branches[repo.value]?.contains("feat-branch") == false)
    }

    @Test
    fun `ListPipelineRunsUseCase filters by branch and status`() {
        val forges = InMemoryForges()
        val repo = RepositorySlug.of("FilipKrawiec/devcontainer")
        val run1 = PipelineRun(
            id = PipelineRunId.of(101L),
            workflow = WorkflowName.of("CI"),
            branch = BranchName.of("main"),
            status = PipelineStatus.SUCCESS,
            conclusion = "success",
            event = "push",
            commitSha = "abc1234",
            createdAt = "2026-08-19T10:00:00Z",
            url = "https://github.com/FilipKrawiec/devcontainer/actions/runs/101"
        )
        val run2 = PipelineRun(
            id = PipelineRunId.of(102L),
            workflow = WorkflowName.of("CI"),
            branch = BranchName.of("feat/pipeline"),
            status = PipelineStatus.FAILURE,
            conclusion = "failure",
            event = "pull_request",
            commitSha = "def5678",
            createdAt = "2026-08-19T11:00:00Z",
            url = "https://github.com/FilipKrawiec/devcontainer/actions/runs/102"
        )
        forges.pipelineRuns[101L] = run1
        forges.pipelineRuns[102L] = run2

        val useCase = ListPipelineRunsUseCase(forges)
        val allRuns = useCase.execute(repo)
        assertIs<ListPipelineRunsUseCase.Outcome.Success>(allRuns)
        assertEquals(2, allRuns.runs.size)

        val branchFiltered = useCase.execute(repo, branch = BranchName.of("main"))
        assertIs<ListPipelineRunsUseCase.Outcome.Success>(branchFiltered)
        assertEquals(1, branchFiltered.runs.size)
        assertEquals(101L, branchFiltered.runs.first().id.value)

        val statusFiltered = useCase.execute(repo, status = "failure")
        assertIs<ListPipelineRunsUseCase.Outcome.Success>(statusFiltered)
        assertEquals(1, statusFiltered.runs.size)
        assertEquals(102L, statusFiltered.runs.first().id.value)
    }

    @Test
    fun `GetPipelineRunUseCase returns run with jobs and steps`() {
        val forges = InMemoryForges()
        val repo = RepositorySlug.of("FilipKrawiec/devcontainer")
        val job = PipelineJob(
            id = JobId.of(501L),
            name = "build-and-test",
            status = "completed",
            conclusion = "success",
            startedAt = "2026-08-19T10:00:00Z",
            completedAt = "2026-08-19T10:01:30Z",
            url = "https://github.com/FilipKrawiec/devcontainer/actions/jobs/501",
            steps = listOf(
                JobStep(1, "Checkout", "completed", "success"),
                JobStep(2, "Gradle Test", "completed", "success")
            )
        )
        val run = PipelineRun(
            id = PipelineRunId.of(101L),
            workflow = WorkflowName.of("CI"),
            branch = BranchName.of("main"),
            status = PipelineStatus.SUCCESS,
            conclusion = "success",
            event = "push",
            commitSha = "abc1234",
            createdAt = "2026-08-19T10:00:00Z",
            url = "https://github.com/FilipKrawiec/devcontainer/actions/runs/101",
            jobs = listOf(job)
        )
        forges.pipelineRuns[101L] = run

        val useCase = GetPipelineRunUseCase(forges)
        val outcome = useCase.execute(repo, PipelineRunId.of(101L))
        assertIs<GetPipelineRunUseCase.Outcome.Success>(outcome)
        assertEquals(101L, outcome.run.id.value)
        assertEquals(1, outcome.run.jobs.size)
        assertEquals(2, outcome.run.jobs.first().steps.size)
    }

    @Test
    fun `GetJobTraceUseCase retrieves and filters trace logs`() {
        val forges = InMemoryForges()
        val repo = RepositorySlug.of("FilipKrawiec/devcontainer")
        val logData = """
            2026-08-19T10:00:01Z [INFO] Starting build...
            2026-08-19T10:00:05Z [INFO] Compiling Kotlin sources...
            2026-08-19T10:00:10Z [ERROR] Failed to compile: unresolved reference
            2026-08-19T10:00:12Z [FATAL] Task execution terminated
            2026-08-19T10:00:13Z [INFO] Finished in 12s
        """.trimIndent()
        forges.jobTraces[501L] = logData

        val useCase = GetJobTraceUseCase(forges)

        // Raw
        val rawOutcome = useCase.execute(repo, JobId.of(501L))
        assertIs<GetJobTraceUseCase.Outcome.Success>(rawOutcome)
        assertEquals(5, rawOutcome.matchedLines.size)

        // Grep regex
        val grepOutcome = useCase.execute(repo, JobId.of(501L), pattern = "Compiling|unresolved")
        assertIs<GetJobTraceUseCase.Outcome.Success>(grepOutcome)
        assertEquals(2, grepOutcome.matchedLines.size)

        // Failed-only
        val failedOutcome = useCase.execute(repo, JobId.of(501L), failedOnly = true)
        assertIs<GetJobTraceUseCase.Outcome.Success>(failedOutcome)
        assertEquals(2, failedOutcome.matchedLines.size)
        assertTrue(failedOutcome.matchedLines.any { it.contains("ERROR") })
        assertTrue(failedOutcome.matchedLines.any { it.contains("FATAL") })
    }

    @Test
    fun `Json serialization produces expected DTO output`() {
        val prDto = PullRequestDto(11L, "PR Title", "Body", "feat", "main", "https://github.com/owner/repo/pull/11")
        val prJson = ForgeJsonFormat.toJson(prDto)
        assertTrue(prJson.contains("\"number\": 11"))
        assertTrue(prJson.contains("\"head\": \"feat\""))

        val mergeDto = MergeResultDto(11L, "squash", branchDeleted = true)
        val mergeJson = ForgeJsonFormat.toJson(mergeDto)
        assertTrue(mergeJson.contains("\"branchDeleted\": true"))

        val reviewDto = ReviewResultDto(11L, "approve", "Approved by QA")
        val reviewJson = ForgeJsonFormat.toJson(reviewDto)
        assertTrue(reviewJson.contains("\"verdict\": \"approve\""))

        val runDto = PipelineRunDto(
            id = 200L,
            workflow = "Test Workflow",
            branch = "main",
            status = "success",
            conclusion = "success",
            event = "push",
            commitSha = "12345678",
            createdAt = "2026-08-19T12:00:00Z",
            url = "https://github.com/owner/repo/actions/runs/200",
            jobs = listOf(
                PipelineJobDto(
                    id = 300L,
                    name = "build",
                    status = "completed",
                    conclusion = "success",
                    startedAt = null,
                    completedAt = null,
                    url = "https://github.com/owner/repo/actions/jobs/300"
                )
            )
        )
        val runJson = ForgeJsonFormat.toJson(runDto)
        assertTrue(runJson.contains("\"id\": 200"))
        assertTrue(runJson.contains("\"workflow\": \"Test Workflow\""))
        assertTrue(runJson.contains("\"jobs\": ["))

        val traceDto = JobTraceDto(300L, totalLines = 10, matchedLines = 2, lines = listOf("err1", "err2"))
        val traceJson = ForgeJsonFormat.toJson(traceDto)
        assertTrue(traceJson.contains("\"jobId\": 300"))
        assertTrue(traceJson.contains("\"matchedLines\": 2"))
    }
}
