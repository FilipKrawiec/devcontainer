package devcli.commands.projects

import com.github.ajalt.clikt.core.CliktCommand
import devcli.service.WorkspaceService

class ListCommand(
    private val workspaceService: WorkspaceService = WorkspaceService()
) : CliktCommand(
    name = "list",
    help = "List all active Git project repositories in /projects"
) {
    override fun run() {
        val workspaces = workspaceService.listWorkspaces()

        echo("WORKSPACE REPOSITORIES IN /projects:")
        echo("--------------------------------------------------")
        if (workspaces.isEmpty()) {
            echo(" (No repositories found. Use 'dev projects clone <repo-url>' to add one)")
        } else {
            workspaces.forEach { echo(" - $it") }
        }
    }
}
