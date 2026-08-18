package devcli.commands.projects

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import devcli.service.GetAction
import devcli.service.WorkspaceService
import devcli.ui.Theme

class GetCommand(
    private val workspaceService: WorkspaceService = WorkspaceService()
) : CliktCommand(
    name = "get",
    help = "Get a project repository (clone if new, or fetch latest Git state if already cloned)"
) {
    private val repoRef by argument(
        name = "REPO_REF",
        help = "Optional project name, relative path, or remote URL (e.g. user/repo, github.com/user/repo, or git@...). If omitted, fetches all projects."
    ).optional()

    override fun run() {
        if (repoRef.isNullOrBlank()) {
            echo("${Theme.iconStep} ${Theme.boldText("Syncing all active projects in /projects...")}")
        } else {
            echo("${Theme.iconStep} ${Theme.boldText("Resolving project repository:")} ${Theme.code(repoRef!!)}")
        }

        workspaceService.getWorkspace(repoRef)
            .onSuccess { summaries ->
                if (summaries.isEmpty()) {
                    echo(Theme.info("No active project repositories found in /projects."))
                    echo(Theme.muted("Use ${Theme.code("dev get <owner/repo>")} to clone a repository."))
                    return
                }

                val failures = summaries.filter { !it.success }
                if (failures.isNotEmpty()) {
                    failures.forEach { f ->
                        val actionStr = if (f.action == GetAction.CLONED) "clone" else "fetch"
                        echo("${Theme.iconFailure} ${Theme.danger("$actionStr failed for ${f.relativePath}:")} ${f.errorMessage}", err = true)
                    }
                    throw ProgramResult(1)
                } else {
                    summaries.forEach { s ->
                        val verb = if (s.action == GetAction.CLONED) "Cloned" else "Fetched"
                        val fullPath = "/projects/${s.relativePath}"
                        echo("${Theme.iconSuccess} ${Theme.success("Successfully $verb")} ${Theme.highlight(s.relativePath)}")
                        if (s.action == GetAction.CLONED) {
                            echo("  ${Theme.iconBullet} Location: ${Theme.muted(fullPath)}")
                            echo("  ${Theme.iconInfo} Quick navigate: ${Theme.code("cd $fullPath")}")
                        }
                    }
                }
            }
            .onFailure { error ->
                echo("${Theme.iconFailure} ${Theme.danger("Error: ${error.message}")}", err = true)
                throw ProgramResult(1)
            }
    }
}
