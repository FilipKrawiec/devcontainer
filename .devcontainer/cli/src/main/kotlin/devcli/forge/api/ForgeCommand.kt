package devcli.forge.api

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
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
import devcli.forge.domain.PullRequestNumber
import devcli.forge.domain.RepositorySlug
import devcli.forge.domain.ReviewVerdict
import devcli.issuetracker.api.resolveCurrentRepo

class ForgeCommand(
    forges: Forges
) : CliktCommand(
    name = "forge",
    help = "Manage VCS branches, pull requests, reviews, and merges",
    invokeWithoutSubcommand = true
) {
    init {
        subcommands(
            BranchGroup(forges),
            PrGroup(forges)
        )
    }

    override fun run() {
        if (currentContext.invokedSubcommand == null) {
            echo(getFormattedHelp())
        }
    }

    private class BranchGroup(forges: Forges) : CliktCommand(
        name = "branch",
        help = "Manage Git branches",
        invokeWithoutSubcommand = true
    ) {
        init {
            subcommands(CreateBranchCommand(forges))
        }

        override fun run() {
            if (currentContext.invokedSubcommand == null) {
                echo(getFormattedHelp())
            }
        }
    }

    private class CreateBranchCommand(private val forges: Forges) : CliktCommand(
        name = "create",
        help = "Create a new Git branch off a base branch"
    ) {
        private val name by argument(help = "New branch name")
        private val base by option("--base", "-b", help = "Base branch name").default("main")
        private val repo by option("--repo", "-r", help = "Target repository (owner/repo)")
        private val json by option("--json", help = "Emit output in JSON format").flag(default = false)

        override fun run() {
            val useCase = CreateBranchUseCase(forges)
            val slug = RepositorySlug.of(resolveCurrentRepo(repo).value)
            val branchName = BranchName.of(name)
            val baseName = BranchName.of(base)

            val outcome = useCase.execute(slug, branchName, baseName)
            when (outcome) {
                is CreateBranchUseCase.Outcome.Success -> {
                    if (json) {
                        echo(ForgeJsonFormat.toJson(BranchResultDto(outcome.branch.value, outcome.base.value)))
                    } else {
                        echo("✔ Created branch '${outcome.branch.value}' off '${outcome.base.value}' in ${slug.value}")
                    }
                }
                is CreateBranchUseCase.Outcome.Failure -> {
                    if (json) {
                        echo(ForgeJsonFormat.toJson(ForgeErrorDto(outcome.message)))
                    } else {
                        echo("✘ Failed to create branch: ${outcome.message}", err = true)
                    }
                    throw com.github.ajalt.clikt.core.ProgramResult(1)
                }
            }
        }
    }

    private class PrGroup(forges: Forges) : CliktCommand(
        name = "pr",
        help = "Manage Pull Requests",
        invokeWithoutSubcommand = true
    ) {
        init {
            subcommands(
                CreatePrCommand(forges),
                ReviewPrCommand(forges),
                MergePrCommand(forges)
            )
        }

        override fun run() {
            if (currentContext.invokedSubcommand == null) {
                echo(getFormattedHelp())
            }
        }
    }

    private class CreatePrCommand(private val forges: Forges) : CliktCommand(
        name = "create",
        help = "Open a new Pull Request"
    ) {
        private val title by option("--title", "-t", help = "PR title").required()
        private val body by option("--body", "-b", help = "PR description").default("")
        private val head by option("--head", help = "Source branch name").required()
        private val base by option("--base", help = "Target base branch name").default("main")
        private val repo by option("--repo", "-r", help = "Target repository (owner/repo)")
        private val json by option("--json", help = "Emit output in JSON format").flag(default = false)

        override fun run() {
            val useCase = OpenPullRequestUseCase(forges)
            val slug = RepositorySlug.of(resolveCurrentRepo(repo).value)
            val outcome = useCase.execute(
                slug,
                PrTitle.of(title),
                PrBody.of(body),
                BranchName.of(head),
                BranchName.of(base)
            )

            when (outcome) {
                is OpenPullRequestUseCase.Outcome.Success -> {
                    if (json) {
                        echo(ForgeJsonFormat.toJson(PullRequestDto.fromDomain(outcome.pullRequest)))
                    } else {
                        echo("✔ Opened PR #${outcome.pullRequest.number.value}: ${outcome.pullRequest.title.value}")
                        echo("  URL: ${outcome.pullRequest.url}")
                        echo("  ${outcome.pullRequest.head.value} -> ${outcome.pullRequest.base.value}")
                    }
                }
                is OpenPullRequestUseCase.Outcome.Failure -> {
                    if (json) {
                        echo(ForgeJsonFormat.toJson(ForgeErrorDto(outcome.message)))
                    } else {
                        echo("✘ Failed to open PR: ${outcome.message}", err = true)
                    }
                    throw com.github.ajalt.clikt.core.ProgramResult(1)
                }
            }
        }
    }

    private class ReviewPrCommand(private val forges: Forges) : CliktCommand(
        name = "review",
        help = "Submit a formal PR review"
    ) {
        private val id by argument(help = "Pull Request number")
        private val verdict by option("--verdict", "-v", help = "Verdict (approve, comment, request-changes)").default("approve")
        private val body by option("--body", "-b", help = "Review notes").default("")
        private val repo by option("--repo", "-r", help = "Target repository (owner/repo)")
        private val json by option("--json", help = "Emit output in JSON format").flag(default = false)

        override fun run() {
            val useCase = ReviewPullRequestUseCase(forges)
            val slug = RepositorySlug.of(resolveCurrentRepo(repo).value)
            val prNumber = PullRequestNumber.of(id)
            val reviewVerdict = ReviewVerdict.of(verdict)
            val outcome = useCase.execute(slug, prNumber, reviewVerdict, CommentBody.of(body.ifBlank { "Review submitted" }))

            when (outcome) {
                is ReviewPullRequestUseCase.Outcome.Success -> {
                    if (json) {
                        echo(ForgeJsonFormat.toJson(ReviewResultDto(prNumber.value, reviewVerdict.name.lowercase(), body)))
                    } else {
                        echo("✔ Submitted ${reviewVerdict.name.lowercase()} review to PR #${prNumber.value}")
                    }
                }
                is ReviewPullRequestUseCase.Outcome.Failure -> {
                    if (json) {
                        echo(ForgeJsonFormat.toJson(ForgeErrorDto(outcome.message)))
                    } else {
                        echo("✘ Failed to submit review: ${outcome.message}", err = true)
                    }
                    throw com.github.ajalt.clikt.core.ProgramResult(1)
                }
            }
        }
    }

    private class MergePrCommand(private val forges: Forges) : CliktCommand(
        name = "merge",
        help = "Merge a Pull Request"
    ) {
        private val id by argument(help = "Pull Request number")
        private val strategy by option("--strategy", "-s", help = "Strategy (squash, rebase, merge)").default("squash")
        private val deleteBranch by option("--delete-branch", "-d", help = "Delete head branch after merge").flag(default = true)
        private val repo by option("--repo", "-r", help = "Target repository (owner/repo)")
        private val json by option("--json", help = "Emit output in JSON format").flag(default = false)

        override fun run() {
            val useCase = MergePullRequestUseCase(forges)
            val slug = RepositorySlug.of(resolveCurrentRepo(repo).value)
            val prNumber = PullRequestNumber.of(id)
            val mergeStrategy = MergeStrategy.of(strategy)
            val outcome = useCase.execute(slug, prNumber, mergeStrategy, deleteBranch)

            when (outcome) {
                is MergePullRequestUseCase.Outcome.Success -> {
                    if (json) {
                        echo(ForgeJsonFormat.toJson(MergeResultDto(prNumber.value, mergeStrategy.name.lowercase(), deleteBranch)))
                    } else {
                        echo("✔ Successfully merged PR #${prNumber.value} with ${mergeStrategy.name.lowercase()}${if (deleteBranch) " (and deleted branch)" else ""}")
                    }
                }
                is MergePullRequestUseCase.Outcome.Failure -> {
                    if (json) {
                        echo(ForgeJsonFormat.toJson(ForgeErrorDto(outcome.message)))
                    } else {
                        echo("✘ Failed to merge PR: ${outcome.message}", err = true)
                    }
                    throw com.github.ajalt.clikt.core.ProgramResult(1)
                }
            }
        }
    }
}
