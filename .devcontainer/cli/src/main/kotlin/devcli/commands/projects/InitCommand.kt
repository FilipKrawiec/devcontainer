package devcli.commands.projects

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import devcli.service.WorkspaceService
import devcli.ui.Theme

class InitCommand(
    private val workspaceService: WorkspaceService = WorkspaceService()
) : CliktCommand(
    name = "init",
    help = "Initialize and scaffold a new monorepo project repository in /projects"
) {
    private val repoRef by argument(
        name = "REPO_REF",
        help = "Project name, relative path, or repository identifier (e.g. user/my-project or github.com/user/my-project)"
    )

    private val description by option(
        "-d", "--description",
        help = "Project description for README.md"
    )

    private val noGit by option(
        "--no-git",
        help = "Skip git repository initialization"
    ).flag(default = false)

    private val noCommit by option(
        "--no-commit",
        help = "Skip initial git commit after scaffolding"
    ).flag(default = false)

    override fun run() {
        echo("${Theme.iconStep} ${Theme.boldText("Initializing new project repository:")} ${Theme.code(repoRef)}")

        workspaceService.initWorkspace(
            repoRef = repoRef,
            description = description,
            initGit = !noGit,
            commit = !noCommit && !noGit
        ).onSuccess { summary ->
            echo("${Theme.iconSuccess} ${Theme.success("Successfully initialized project")} ${Theme.highlight(summary.relativePath)}")
            echo("  ${Theme.iconBullet} Location: ${Theme.muted(summary.targetDirectory)}")
            if (summary.gitInitialized) {
                val commitMsg = if (summary.commitCreated) "with initial commit" else "without initial commit"
                echo("  ${Theme.iconBullet} Git: ${Theme.muted("Initialized branch main ($commitMsg)")}")
            }
            echo("  ${Theme.iconInfo} Quick navigate: ${Theme.code("cd ${summary.targetDirectory}")}")
        }.onFailure { error ->
            echo("${Theme.iconFailure} ${Theme.danger("Error: ${error.message}")}", err = true)
            throw ProgramResult(1)
        }
    }
}
