package devcli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.PrintHelpMessage
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import java.io.File

class RootCommand : CliktCommand(
    name = "dev",
    help = "Master Dev Workspace CLI - Managed container development environment"
) {
    override fun run() = Unit
}

class CloneCommand : CliktCommand(
    name = "clone",
    help = "Clone a Git repository into /projects"
) {
    private val repoUrl by argument(help = "Git remote SSH or HTTPS URL (e.g. git@gitlab.com:group/project.git)")

    override fun run() {
        var raw = repoUrl.trim().removeSuffix(".git")
        raw = when {
            raw.startsWith("ssh://git@") -> raw.removePrefix("ssh://git@").substringAfter('/')
            raw.startsWith("git@") && raw.contains(':') -> {
                val host = raw.removePrefix("git@").substringBefore(':')
                val path = raw.substringAfter(':')
                "$host/$path"
            }
            raw.startsWith("https://") || raw.startsWith("http://") -> raw.substringAfter("://").substringAfter('/')
            else -> raw
        }
        val targetPath = "/projects/${raw.trim('/')}"
        val targetDir = File(targetPath)

        if (targetDir.exists() && File(targetDir, ".git").exists()) {
            echo("error: repository already exists at $targetPath", err = true)
            return
        }

        echo("Cloning $repoUrl into $targetPath...")
        targetDir.parentFile?.mkdirs()

        val process = ProcessBuilder("git", "clone", repoUrl, targetPath)
            .inheritIO()
            .start()

        val exitCode = process.waitFor()
        if (exitCode == 0) {
            echo("Successfully cloned into $targetPath")
        } else {
            echo("error: git clone failed with exit code $exitCode", err = true)
        }
    }
}

class ListCommand : CliktCommand(
    name = "list",
    help = "List all active repositories in /projects"
) {
    override fun run() {
        val projectsDir = File("/projects")
        if (!projectsDir.exists()) {
            echo("No /projects volume mounted.")
            return
        }

        echo("WORKSPACE REPOSITORIES IN /projects:")
        echo("--------------------------------------------------")
        var count = 0
        projectsDir.walkTopDown()
            .maxDepth(6)
            .filter { it.isDirectory && it.name == ".git" }
            .forEach { gitDir ->
                val repoDir = gitDir.parentFile
                val relPath = repoDir.relativeTo(projectsDir).path
                echo(" - $relPath")
                count++
            }

        if (count == 0) {
            echo(" (No repositories found. Use 'dev clone <repo-url>' to add one)")
        }
    }
}

class ResetCommand : CliktCommand(
    name = "reset",
    help = "Remove a repository from /projects"
) {
    private val repoPath by argument(help = "Relative path of repository in /projects (e.g. gitlab.com/group/project)")

    override fun run() {
        val targetDir = File("/projects/${repoPath.trim('/')}")
        if (!targetDir.exists()) {
            echo("error: repository not found at ${targetDir.path}", err = true)
            return
        }

        echo("Removing ${targetDir.path}...")
        targetDir.deleteRecursively()
        echo("Reset complete.")
    }
}

fun main(args: Array<String>) {
    val cmd = RootCommand().subcommands(CloneCommand(), ListCommand(), ResetCommand())
    try {
        cmd.parse(args)
    } catch (e: PrintHelpMessage) {
        println(cmd.getFormattedHelp())
    } catch (e: PrintMessage) {
        println(e.message)
    } catch (e: CliktError) {
        System.err.println(e.message)
    }
}
