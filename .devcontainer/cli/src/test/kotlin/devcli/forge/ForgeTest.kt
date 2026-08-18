package devcli.forge

import devcli.forge.api.ForgeJsonFormat
import devcli.forge.api.MergeResultDto
import devcli.forge.api.PullRequestDto
import devcli.forge.api.ReviewResultDto
import devcli.forge.app.CreateBranchUseCase
import devcli.forge.app.MergePullRequestUseCase
import devcli.forge.app.OpenPullRequestUseCase
import devcli.forge.app.ReviewPullRequestUseCase
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
    }
}
