package devcli.commands.projects

import com.github.ajalt.clikt.core.CliktCommand

class ProjectsCommand : CliktCommand(
    name = "projects",
    help = "Manage Git project repositories in /projects volume"
) {
    override fun run() = Unit
}
