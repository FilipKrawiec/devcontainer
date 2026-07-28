package devcli.commands.projects

import com.github.ajalt.clikt.core.CliktCommand
import devcli.service.WorkspaceService

class ListCommand(
    private val workspaceService: WorkspaceService = WorkspaceService()
) : CliktCommand(
    name = "list",
    help = "List all active project repositories in /projects with HEAD and Git staleness"
) {
    override fun run() {
        val details = workspaceService.listWorkspaceDetails()

        if (details.isEmpty()) {
            echo("No active project repositories found in /projects. Use 'dev projects get <repo-url>' to add one.")
            return
        }

        val maxProjectLen = (details.map { it.relativePath.length } + 7).maxOrNull() ?: 7
        val maxHeadLen = (details.map { it.headRef.length } + 4).maxOrNull() ?: 4
        val maxStalenessLen = (details.map { it.staleness.length } + 9).maxOrNull() ?: 9

        val projectWidth = maxOf(maxProjectLen, 30)
        val headWidth = maxOf(maxHeadLen, 12)
        val stalenessWidth = maxOf(maxStalenessLen, 15)

        fun formatRow(col1: String, col2: String, col3: String): String {
            return String.format("%-${projectWidth}s  %-${headWidth}s  %-${stalenessWidth}s", col1, col2, col3)
        }

        val header = formatRow("PROJECT", "HEAD", "STALENESS")
        val separator = "-".repeat(header.length)

        echo(header)
        echo(separator)
        details.forEach { detail ->
            echo(formatRow(detail.relativePath, detail.headRef, detail.staleness))
        }
    }
}
