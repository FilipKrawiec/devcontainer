package devcli

import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.PrintHelpMessage
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.core.subcommands
import devcli.commands.RootCommand
import devcli.commands.projects.CloneCommand
import devcli.commands.projects.FetchCommand
import devcli.commands.projects.ListCommand
import devcli.commands.projects.ProjectsCommand
import devcli.commands.projects.ResetCommand
import devcli.service.WorkspaceService

fun main(args: Array<String>) {
    val service = WorkspaceService()
    val projectsCommand = ProjectsCommand().subcommands(
        CloneCommand(service),
        ListCommand(service),
        FetchCommand(service),
        ResetCommand(service)
    )
    val rootCommand = RootCommand().subcommands(projectsCommand)
    rootCommand.main(args)
}
