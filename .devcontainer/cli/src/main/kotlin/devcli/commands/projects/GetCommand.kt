package devcli.commands.projects

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import devcli.service.GetAction
import devcli.service.WorkspaceService

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
        workspaceService.getWorkspace(repoRef)
            .onSuccess { summaries ->
                if (summaries.isEmpty()) {
                    echo("No active project repositories found in /projects.")
                } else {
                    val failures = summaries.filter { !it.success }
                    if (failures.isNotEmpty()) {
                        failures.forEach { f ->
                            val actionStr = if (f.action == GetAction.CLONED) "clone" else "fetch"
                            echo("error: $actionStr failed for ${f.relativePath}: ${f.errorMessage}", err = true)
                        }
                        throw ProgramResult(1)
                    } else {
                        summaries.forEach { s ->
                            val verb = if (s.action == GetAction.CLONED) "Cloned" else "Fetched"
                            echo("Successfully $verb ${s.relativePath}")
                        }
                    }
                }
            }
            .onFailure { error ->
                echo("error: ${error.message}", err = true)
                throw ProgramResult(1)
            }
    }
}
