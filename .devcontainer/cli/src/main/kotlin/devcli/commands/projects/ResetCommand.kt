package devcli.commands.projects

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import devcli.service.WorkspaceService
import devcli.ui.Theme

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
    private val confirmed by option("--yes", "-y", help = "Confirm permanent removal of the repository working tree").flag(default = false)

    override fun run() {
        val isInteractive = System.console() != null
        val shouldProceed = if (confirmed) {
            true
        } else if (isInteractive) {
            echo("${Theme.iconWarning} ${Theme.warning("You are about to permanently delete the repository at:")}")
            echo("  ${Theme.code("/projects/$repoPath")}")
            echo("  ${Theme.muted("Any uncommitted changes or untracked files will be permanently lost.")}")
            val input = terminal.prompt(
                prompt = "Are you sure you want to delete this repository? (y/N)",
                default = "n"
            )
            input?.trim()?.equals("y", ignoreCase = true) == true ||
                input?.trim()?.equals("yes", ignoreCase = true) == true
        } else {
            echo("${Theme.iconFailure} ${Theme.danger("Refusing to remove $repoPath without --yes in non-interactive mode. No files were changed.")}", err = true)
            throw ProgramResult(2)
        }

        if (!shouldProceed) {
            echo("${Theme.iconInfo} ${Theme.info("Reset operation cancelled. No files were changed.")}")
            return
        }

        echo("${Theme.iconStep} Removing repository ${Theme.highlight(repoPath)}...")
        workspaceService.removeWorkspace(repoPath)
            .onSuccess { path ->
                echo("${Theme.iconSuccess} ${Theme.success("Successfully removed")} ${Theme.muted(path)}")
            }
            .onFailure { error ->
                echo("${Theme.iconFailure} ${Theme.danger("Error: ${error.message}")}", err = true)
                throw ProgramResult(1)
            }
    }
}
