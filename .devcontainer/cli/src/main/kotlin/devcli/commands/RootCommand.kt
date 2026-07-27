package devcli.commands

import com.github.ajalt.clikt.core.CliktCommand

class RootCommand : CliktCommand(
    name = "dev",
    help = "Master Dev Workspace CLI - Managed container development environment"
) {
    override fun run() = Unit
}
