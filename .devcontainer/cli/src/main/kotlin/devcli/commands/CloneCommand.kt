package devcli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import devcli.service.WorkspaceService

class CloneCommand(
    private val workspaceService: WorkspaceService = WorkspaceService()
) : CliktCommand(
    name = "clone",
    help = "Clone a Git repository into /projects"
) {
    private val repoUrl by argument(help = "Git remote SSH or HTTPS URL (e.g. git@gitlab.com:group/project.git)")

    override fun run() {
        echo("Cloning $repoUrl into /projects...")
        workspaceService.cloneWorkspace(repoUrl)
            .onSuccess { path -> echo("Successfully cloned into $path") }
            .onFailure { error -> echo("error: ${error.message}", err = true) }
    }
}
