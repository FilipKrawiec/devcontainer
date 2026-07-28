package devcli.commands.projects

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import devcli.service.WorkspaceService

class FetchCommand(
    private val workspaceService: WorkspaceService = WorkspaceService()
) : CliktCommand(
    name = "fetch",
    help = "Fetch latest Git remotes for active project repositories in /projects"
) {
    private val repoPath by argument(
        name = "REPO_PATH",
        help = "Optional repository name or relative path in /projects (e.g., group/project or repo name)"
    ).optional()

    private val prune by option(
        "-p", "--prune",
        help = "Before fetching, remove any remote-tracking references that no longer exist on the remote"
    ).flag(default = false)

    private val tags by option(
        "-t", "--tags",
        help = "Fetch all tags from the remote"
    ).flag(default = false)

    override fun run() {
        workspaceService.fetchWorkspaces(repoPath, prune = prune, tags = tags)
            .onSuccess { summaries ->
                if (summaries.isEmpty()) {
                    echo("No active repositories found in /projects.")
                } else {
                    val failures = summaries.filter { !it.success }
                    if (failures.isNotEmpty()) {
                        echo("Completed with ${failures.size} error(s).", err = true)
                        throw ProgramResult(1)
                    }
                }
            }
            .onFailure { error ->
                echo("error: ${error.message}", err = true)
                throw ProgramResult(1)
            }
    }
}
