package devcli

import com.github.ajalt.clikt.core.subcommands
import devcli.commands.DoctorCommand
import devcli.commands.RootCommand
import devcli.commands.projects.GetCommand
import devcli.commands.projects.ListCommand
import devcli.commands.projects.ProjectsCommand
import devcli.commands.projects.ResetCommand
import devcli.forge.api.ForgeCommand
import devcli.forge.infra.GitHubHttpForges
import devcli.issuetracker.api.IssueTrackerCommand
import devcli.issuetracker.infra.GitHubGraphQLWorkItems
import devcli.service.WorkspaceService

fun main(args: Array<String>) {
    val service = WorkspaceService()
    val workItems = GitHubGraphQLWorkItems()
    val forges = GitHubHttpForges()

    // Structured projects subcommands
    val projectsCommand = ProjectsCommand().subcommands(
        GetCommand(service),
        ListCommand(service),
        ResetCommand(service)
    )

    // Root command with both structured groups and top-level ergonomic aliases
    val rootCommand = RootCommand().subcommands(
        DoctorCommand(),
        projectsCommand,
        IssueTrackerCommand(workItems),
        ForgeCommand(forges),
        GetCommand(service),
        ListCommand(service),
        ResetCommand(service)
    )

    rootCommand.main(args)
}
