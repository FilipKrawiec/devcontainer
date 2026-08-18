package devcli.issuetracker.api

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import devcli.issuetracker.app.AddCommentUseCase
import devcli.issuetracker.app.CreateWorkItemUseCase
import devcli.issuetracker.app.GetWorkItemUseCase
import devcli.issuetracker.app.UpdateWorkItemPhaseUseCase
import devcli.issuetracker.domain.CommentBody
import devcli.issuetracker.domain.DeliveryPhase
import devcli.issuetracker.domain.RepositorySlug
import devcli.issuetracker.domain.WorkItemBody
import devcli.issuetracker.domain.WorkItemId
import devcli.issuetracker.domain.WorkItemTitle
import devcli.issuetracker.domain.WorkItemType
import devcli.issuetracker.domain.WorkItems
import java.io.File

fun resolveCurrentRepo(repoParam: String?): RepositorySlug {
    if (!repoParam.isNullOrBlank()) {
        return RepositorySlug.of(repoParam)
    }

    // Try detecting from current working directory (e.g. /projects/github.com/owner/repo)
    val cwd = File(System.getProperty("user.dir") ?: ".").canonicalPath
    val match = Regex("""github\.com/([^/]+)/([^/]+)""").find(cwd)
    if (match != null) {
        val (owner, repo) = match.destructured
        return RepositorySlug.of("$owner/$repo")
    }

    return RepositorySlug.of("FilipKrawiec/devcontainer")
}

class IssueTrackerCommand(
    workItems: WorkItems
) : CliktCommand(
    name = "issuetracker",
    help = "Manage work items, backlog, and delivery board phases",
    invokeWithoutSubcommand = true
) {
    init {
        subcommands(
            CreateCommand(workItems),
            GetCommand(workItems),
            SetPhaseCommand(workItems),
            CommentCommand(workItems)
        )
    }

    override fun run() = Unit

    private class CreateCommand(private val workItems: WorkItems) : CliktCommand(
        name = "create",
        help = "Create a new work item on the backlog"
    ) {
        private val title by option("--title", "-t", help = "Work item title").required()
        private val body by option("--body", "-b", help = "Work item description").default("")
        private val type by option("--type", help = "Type (feature, bug, task, story)").default("feature")
        private val repo by option("--repo", "-r", help = "Target repository (owner/repo)")
        private val json by option("--json", help = "Emit output in JSON format").flag(default = false)

        override fun run() {
            val useCase = CreateWorkItemUseCase(workItems)
            val slug = resolveCurrentRepo(repo)
            val itemType = WorkItemType.of(type)
            val outcome = useCase.execute(slug, WorkItemTitle.of(title), WorkItemBody.of(body), itemType)

            when (outcome) {
                is CreateWorkItemUseCase.Outcome.Success -> {
                    if (json) {
                        echo(JsonFormat.toJson(WorkItemDto.fromDomain(outcome.workItem)))
                    } else {
                        echo("✔ Created ${outcome.workItem.type.name.lowercase()} #${outcome.workItem.id.value}: ${outcome.workItem.title.value}")
                        if (outcome.workItem.url != null) {
                            echo("  URL: ${outcome.workItem.url}")
                        }
                        echo("  Phase: ${outcome.workItem.phase.displayName}")
                    }
                }
                is CreateWorkItemUseCase.Outcome.Failure -> {
                    if (json) {
                        echo(JsonFormat.toJson(ErrorDto(outcome.message)))
                    } else {
                        echo("✘ Failed to create work item: ${outcome.message}", err = true)
                    }
                    throw com.github.ajalt.clikt.core.ProgramResult(1)
                }
            }
        }
    }

    private class GetCommand(private val workItems: WorkItems) : CliktCommand(
        name = "get",
        help = "Get details of a work item by ID"
    ) {
        private val id by argument(help = "Work item number")
        private val repo by option("--repo", "-r", help = "Target repository (owner/repo)")
        private val json by option("--json", help = "Emit output in JSON format").flag(default = false)

        override fun run() {
            val useCase = GetWorkItemUseCase(workItems)
            val slug = resolveCurrentRepo(repo)
            val itemId = WorkItemId.of(id)
            val outcome = useCase.execute(slug, itemId)

            when (outcome) {
                is GetWorkItemUseCase.Outcome.Success -> {
                    if (json) {
                        echo(JsonFormat.toJson(WorkItemDto.fromDomain(outcome.workItem)))
                    } else {
                        echo("Work Item #${outcome.workItem.id.value}: ${outcome.workItem.title.value}")
                        echo("  Type:  ${outcome.workItem.type.name.lowercase()}")
                        echo("  Phase: ${outcome.workItem.phase.displayName}")
                        if (outcome.workItem.url != null) {
                            echo("  URL:   ${outcome.workItem.url}")
                        }
                        if (outcome.workItem.body.value.isNotBlank()) {
                            echo("\n${outcome.workItem.body.value}")
                        }
                    }
                }
                is GetWorkItemUseCase.Outcome.NotFound -> {
                    if (json) {
                        echo(JsonFormat.toJson(ErrorDto(outcome.message)))
                    } else {
                        echo("✘ ${outcome.message}", err = true)
                    }
                    throw com.github.ajalt.clikt.core.ProgramResult(1)
                }
                is GetWorkItemUseCase.Outcome.Failure -> {
                    if (json) {
                        echo(JsonFormat.toJson(ErrorDto(outcome.message)))
                    } else {
                        echo("✘ ${outcome.message}", err = true)
                    }
                    throw com.github.ajalt.clikt.core.ProgramResult(1)
                }
            }
        }
    }

    private class SetPhaseCommand(private val workItems: WorkItems) : CliktCommand(
        name = "set-phase",
        help = "Update the delivery board phase for a work item"
    ) {
        private val id by argument(help = "Work item number")
        private val phase by option("--phase", "-p", help = "Target phase (e.g. 02-spec, 03-plan, 04-execute)").required()
        private val repo by option("--repo", "-r", help = "Target repository (owner/repo)")
        private val json by option("--json", help = "Emit output in JSON format").flag(default = false)

        override fun run() {
            val useCase = UpdateWorkItemPhaseUseCase(workItems)
            val slug = resolveCurrentRepo(repo)
            val itemId = WorkItemId.of(id)
            val targetPhase = DeliveryPhase.of(phase)
            val outcome = useCase.execute(slug, itemId, targetPhase)

            when (outcome) {
                is UpdateWorkItemPhaseUseCase.Outcome.Success -> {
                    if (json) {
                        echo(JsonFormat.toJson(WorkItemDto.fromDomain(outcome.workItem)))
                    } else {
                        echo("✔ Updated work item #${outcome.workItem.id.value} to phase: ${outcome.workItem.phase.displayName}")
                    }
                }
                is UpdateWorkItemPhaseUseCase.Outcome.NotFound -> {
                    if (json) {
                        echo(JsonFormat.toJson(ErrorDto(outcome.message)))
                    } else {
                        echo("✘ ${outcome.message}", err = true)
                    }
                    throw com.github.ajalt.clikt.core.ProgramResult(1)
                }
                is UpdateWorkItemPhaseUseCase.Outcome.Failure -> {
                    if (json) {
                        echo(JsonFormat.toJson(ErrorDto(outcome.message)))
                    } else {
                        echo("✘ Failed to update phase: ${outcome.message}", err = true)
                    }
                    throw com.github.ajalt.clikt.core.ProgramResult(1)
                }
            }
        }
    }

    private class CommentCommand(private val workItems: WorkItems) : CliktCommand(
        name = "comment",
        help = "Post a comment to a work item"
    ) {
        private val id by argument(help = "Work item number")
        private val body by option("--body", "-b", help = "Comment body content").required()
        private val repo by option("--repo", "-r", help = "Target repository (owner/repo)")
        private val json by option("--json", help = "Emit output in JSON format").flag(default = false)

        override fun run() {
            val useCase = AddCommentUseCase(workItems)
            val slug = resolveCurrentRepo(repo)
            val itemId = WorkItemId.of(id)
            val outcome = useCase.execute(slug, itemId, CommentBody.of(body))

            when (outcome) {
                is AddCommentUseCase.Outcome.Success -> {
                    if (json) {
                        echo(JsonFormat.toJson(CommentResponseDto(itemId.value, outcome.commentUrl)))
                    } else {
                        echo("✔ Comment posted to #${itemId.value}: ${outcome.commentUrl}")
                    }
                }
                is AddCommentUseCase.Outcome.Failure -> {
                    if (json) {
                        echo(JsonFormat.toJson(ErrorDto(outcome.message)))
                    } else {
                        echo("✘ Failed to post comment: ${outcome.message}", err = true)
                    }
                    throw com.github.ajalt.clikt.core.ProgramResult(1)
                }
            }
        }
    }
}
