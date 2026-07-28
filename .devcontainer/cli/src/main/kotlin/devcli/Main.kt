package devcli

import com.github.ajalt.clikt.core.subcommands
import devcli.commands.RootCommand
import devcli.commands.projects.GetCommand
import devcli.commands.projects.ListCommand
import devcli.commands.projects.ProjectsCommand
import devcli.commands.projects.ResetCommand
import devcli.service.WorkspaceService

fun main(args: Array<String>) {
    val service = WorkspaceService()
    val projectsCommand = ProjectsCommand().subcommands(
        GetCommand(service),
        ListCommand(service),
        ResetCommand(service)
    )
    val rootCommand = RootCommand().subcommands(projectsCommand)
    rootCommand.main(args)
}
