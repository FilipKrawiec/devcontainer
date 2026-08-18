package devcli.commands.projects

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.mordant.rendering.BorderType
import com.github.ajalt.mordant.table.table
import devcli.service.WorkspaceService
import devcli.ui.Theme

class ListCommand(
    private val workspaceService: WorkspaceService = WorkspaceService()
) : CliktCommand(
    name = "list",
    help = "List active project repositories in /projects with Git branch and upstream sync status"
) {
    override fun run() {
        val details = workspaceService.listWorkspaceDetails()

        if (details.isEmpty()) {
            echo(Theme.info("No active project repositories found in /projects."))
            echo(Theme.muted("Get started by cloning a project with:"))
            echo("  ${Theme.code("dev get <owner/repo>")}")
            echo("  ${Theme.code("dev get https://github.com/<owner>/<repo>.git")}")
            return
        }

        echo(Theme.highlight("Active Projects (/projects)"))
        echo("")

        val projectsTable = table {
            borderType = BorderType.ROUNDED
            header {
                row(
                    Theme.boldText("PROJECT"),
                    Theme.boldText("BRANCH / HEAD"),
                    Theme.boldText("SYNC STATUS")
                )
            }
            body {
                details.forEach { detail ->
                    row(
                        Theme.highlight(detail.relativePath),
                        Theme.branchBadge(detail.headRef),
                        Theme.stalenessBadge(detail.staleness)
                    )
                }
            }
        }

        terminal.println(projectsTable)

        // Summary footer
        val total = details.size
        val dirtyCount = details.count { it.isDirty }
        val upToDateCount = details.count { it.staleness == "Up to date" }

        val summaryParts = mutableListOf<String>()
        if (upToDateCount > 0) summaryParts.add("${Theme.success("$upToDateCount up to date")}")
        if (dirtyCount > 0) summaryParts.add("${Theme.warning("$dirtyCount with uncommitted changes")}")

        val summaryDetail = if (summaryParts.isNotEmpty()) " (${summaryParts.joinToString(", ")})" else ""
        val repoWord = if (total == 1) "repository" else "repositories"
        echo("")
        echo(Theme.muted("Total: $total $repoWord$summaryDetail"))
    }
}
