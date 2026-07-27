package devcli

import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.PrintHelpMessage
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.core.subcommands
import devcli.commands.CloneCommand
import devcli.commands.ListCommand
import devcli.commands.ResetCommand
import devcli.commands.RootCommand
import devcli.service.WorkspaceService

fun main(args: Array<String>) {
    val service = WorkspaceService()
    val rootCommand = RootCommand().subcommands(
        CloneCommand(service),
        ListCommand(service),
        ResetCommand(service)
    )

    try {
        rootCommand.parse(args)
    } catch (e: PrintHelpMessage) {
        println(rootCommand.getFormattedHelp())
    } catch (e: PrintMessage) {
        println(e.message)
    } catch (e: CliktError) {
        System.err.println(e.message)
    }
}
