package devcli.commands

import com.github.ajalt.clikt.core.CliktCommand
import devcli.service.WorkspaceService

class ListCommand(
    private val workspaceService: WorkspaceService = WorkspaceService()
) : CliktCommand(
    name = "list",
    help = "List all active repositories in /projects"
) {
    override fun run() {
        val workspaces = workspaceService.listWorkspaces()

        echo("WORKSPACE REPOSITORIES IN /projects:")
        echo("--------------------------------------------------")
        if (workspaces.isEmpty()) {
            echo(" (No repositories found. Use 'dev clone <repo-url>' to add one)")
        } else {
            workspaces.forEach { echo(" - $it") }
        }
    }
}
