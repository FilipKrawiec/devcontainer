package devcli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import devcli.service.WorkspaceService

class ResetCommand(
    private val workspaceService: WorkspaceService = WorkspaceService()
) : CliktCommand(
    name = "reset",
    help = "Remove a repository from /projects"
) {
    private val repoPath by argument(help = "Relative path of repository in /projects (e.g. gitlab.com/group/project)")

    override fun run() {
        echo("Removing repository $repoPath...")
        workspaceService.removeWorkspace(repoPath)
            .onSuccess { path -> echo("Successfully removed $path") }
            .onFailure { error -> echo("error: ${error.message}", err = true) }
    }
}
