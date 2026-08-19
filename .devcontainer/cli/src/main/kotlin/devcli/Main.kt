package devcli

import com.github.ajalt.clikt.core.subcommands
import devcli.commands.DoctorCommand
import devcli.commands.RootCommand
import devcli.commands.projects.GetCommand
import devcli.commands.projects.InitCommand
import devcli.commands.projects.ListCommand
import devcli.commands.projects.ProjectsCommand
import devcli.commands.projects.ResetCommand
import devcli.forge.api.ForgeCommand
import devcli.forge.infra.GitHubHttpForges
import devcli.issuetracker.api.IssueTrackerCommand
import devcli.issuetracker.infra.GitHubGraphQLWorkItems
import devcli.service.WorkspaceService

fun createDevCli(
    service: WorkspaceService = WorkspaceService(),
    workItems: devcli.issuetracker.domain.WorkItems,
    forges: devcli.forge.domain.Forges
) = RootCommand().subcommands(
    DoctorCommand(),
    ProjectsCommand().subcommands(
        GetCommand(service),
        ListCommand(service),
        ResetCommand(service),
        InitCommand(service)
    ),
    IssueTrackerCommand(workItems),
    ForgeCommand(forges),
    GetCommand(service),
    ListCommand(service),
    ResetCommand(service),
    InitCommand(service)
)

fun main(args: Array<String>) {
    val service = WorkspaceService()
    val workItems = GitHubGraphQLWorkItems()
    val forges = GitHubHttpForges()

    val rootCommand = createDevCli(service, workItems, forges)
    rootCommand.main(args)
}

