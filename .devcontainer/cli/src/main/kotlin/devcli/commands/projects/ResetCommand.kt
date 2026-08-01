package devcli.commands.projects

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import devcli.service.WorkspaceService

class ResetCommand(
    private val workspaceService: WorkspaceService = WorkspaceService()
) : CliktCommand(
    name = "reset",
    help = "Remove a project repository working tree from /projects workspace"
) {
    private val repoPath by argument(
        name = "REPO_PATH",
        help = "Relative path of repository in /projects (e.g., github.com/user/repo or gitlab.com/group/project)"
    )
    private val confirmed by option("--yes", help = "Confirm permanent removal of the repository working tree").flag(default = false)

    override fun run() {
        if (!confirmed) {
            echo("Refusing to remove $repoPath without --yes. No files were changed.", err = true)
            throw ProgramResult(2)
        }
        echo("Removing repository $repoPath...")
        workspaceService.removeWorkspace(repoPath)
            .onSuccess { path -> echo("Successfully removed $path") }
            .onFailure {
                    error -> echo("error: ${error.message}", err = true)
                    throw ProgramResult(1)
            }
    }
}
