package devcli.forge.app

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

class CreateBranchUseCase(private val forges: Forges) {
    sealed interface Outcome {
        data class Success(val branch: BranchName, val base: BranchName) : Outcome
        data class Failure(val message: String) : Outcome
    }

    fun execute(repo: RepositorySlug, branch: BranchName, base: BranchName): Outcome {
        return try {
            forges.createBranch(repo, branch, base)
            Outcome.Success(branch, base)
        } catch (e: Exception) {
            Outcome.Failure(e.message ?: "Failed to create branch")
        }
    }
}

class OpenPullRequestUseCase(private val forges: Forges) {
    sealed interface Outcome {
        data class Success(val pullRequest: PullRequest) : Outcome
        data class Failure(val message: String) : Outcome
    }

    fun execute(repo: RepositorySlug, title: PrTitle, body: PrBody, head: BranchName, base: BranchName): Outcome {
        return try {
            val pr = forges.openPullRequest(repo, title, body, head, base)
            Outcome.Success(pr)
        } catch (e: Exception) {
            Outcome.Failure(e.message ?: "Failed to open pull request")
        }
    }
}

class ReviewPullRequestUseCase(private val forges: Forges) {
    sealed interface Outcome {
        data class Success(val prNumber: PullRequestNumber, val verdict: ReviewVerdict) : Outcome
        data class Failure(val message: String) : Outcome
    }

    fun execute(repo: RepositorySlug, prNumber: PullRequestNumber, verdict: ReviewVerdict, body: CommentBody): Outcome {
        return try {
            forges.submitReview(repo, prNumber, verdict, body)
            Outcome.Success(prNumber, verdict)
        } catch (e: Exception) {
            Outcome.Failure(e.message ?: "Failed to submit review")
        }
    }
}

class MergePullRequestUseCase(private val forges: Forges) {
    sealed interface Outcome {
        data class Success(val prNumber: PullRequestNumber, val strategy: MergeStrategy, val branchDeleted: Boolean) : Outcome
        data class Failure(val message: String) : Outcome
    }

    fun execute(repo: RepositorySlug, prNumber: PullRequestNumber, strategy: MergeStrategy, deleteBranch: Boolean): Outcome {
        return try {
            forges.merge(repo, prNumber, strategy, deleteBranch)
            Outcome.Success(prNumber, strategy, deleteBranch)
        } catch (e: Exception) {
            Outcome.Failure(e.message ?: "Failed to merge pull request")
        }
    }
}
