package devcli.commands.projects

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import devcli.service.WorkspaceService

class CloneCommand(
    private val workspaceService: WorkspaceService = WorkspaceService()
) : CliktCommand(
    name = "clone",
    help = "Clone a Git repository into /projects workspace"
) {
    private val repoUrl by argument(
        name = "REPO_URL",
        help = "Git remote SSH/HTTPS URL or shorthand reference (e.g., git@github.com:user/repo.git, https://gitlab.com/group/repo.git, or user/repo)"
    )

    override fun run() {
        echo("Cloning $repoUrl into /projects...")
        workspaceService.cloneWorkspace(repoUrl)
            .onSuccess { path -> echo("Successfully cloned into $path") }
            .onFailure { error -> echo("error: ${error.message}", err = true) }
    }
}
