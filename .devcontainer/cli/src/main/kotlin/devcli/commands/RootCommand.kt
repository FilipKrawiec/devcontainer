package devcli.commands

import com.github.ajalt.clikt.completion.completionOption
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.versionOption

class RootCommand : CliktCommand(
    name = "dev",
    help = """
        Master Dev Workspace CLI - Managed container development environment

        Common workflows:
          dev doctor                  Verify toolchains, runtimes, agents, and storage
          dev list                    List active project repositories and Git status
          dev get <owner/repo>        Clone or fetch a project repository
          dev reset <owner/repo>      Remove a project working tree
    """.trimIndent()
) {
    init {
        versionOption("1.0.0", names = setOf("--version", "-v"))
        completionOption()
    }

    override fun run() = Unit
}
