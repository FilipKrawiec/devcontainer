package devcli.commands

import com.github.ajalt.clikt.completion.completionOption
import com.github.ajalt.clikt.core.CliktCommand

class RootCommand : CliktCommand(
    name = "dev",
    help = "Master Dev Workspace CLI - Managed container development environment"
) {
    init {
        completionOption()
    }

    override fun run() = Unit
}
