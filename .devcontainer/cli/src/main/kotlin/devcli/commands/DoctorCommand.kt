package devcli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import devcli.service.RuntimeReport
import devcli.service.RuntimeService
import devcli.service.ToolCategory
import devcli.ui.Theme

class DoctorCommand(
    private val runtimeService: RuntimeService = RuntimeService()
) : CliktCommand(
    name = "doctor",
    help = "Inspect Master Dev Workspace health, runtimes, AI agents, and environment services"
) {
    override fun run() {
        echo(Theme.highlight("Master Dev Workspace Diagnostic"))
        echo(Theme.muted("──────────────────────────────────────────────────"))

        val report = runtimeService.inspect()

        // 1. Tool Categories
        ToolCategory.entries.forEach { category ->
            echo("")
            echo(Theme.boldText(category.title))
            val toolsInCategory = report.toolStatuses.filter { it.category == category }
            toolsInCategory.forEach { tool ->
                if (tool.isInstalled) {
                    val versionSuffix = tool.version?.let { " ${Theme.muted("($it)")}" } ?: ""
                    echo("  ${Theme.iconSuccess} ${Theme.code(tool.name)}$versionSuffix")
                } else {
                    echo("  ${Theme.iconFailure} ${Theme.code(tool.name)} ${Theme.danger("(missing)")}")
                }
            }
        }

        // 2. Environment & Infrastructure Checks
        echo("")
        echo(Theme.boldText("Environment & Infrastructure"))

        if (report.projectsWritable) {
            echo("  ${Theme.iconSuccess} Projects volume ${Theme.muted("(${report.projectsPath})")}: ${Theme.success("writable")}")
        } else {
            echo("  ${Theme.iconFailure} Projects volume ${Theme.muted("(${report.projectsPath})")}: ${Theme.danger("not writable")}")
            echo("    ${Theme.iconInfo} ${Theme.muted("Fix with:")} ${Theme.code("sudo chown -R vscode:vscode ${report.projectsPath}")}")
        }

        if (report.dockerSocketAvailable) {
            echo("  ${Theme.iconSuccess} Docker socket ${Theme.muted("(/var/run/docker.sock)")}: ${Theme.success("available")}")
        } else {
            echo("  ${Theme.iconWarning} Docker socket ${Theme.muted("(/var/run/docker.sock)")}: ${Theme.warning("not available / unmounted")}")
            echo("    ${Theme.iconInfo} ${Theme.muted("Tip: Run container with Docker socket mount enabled to build images or run siblings.")}")
        }

        if (report.previewSidecarAvailable) {
            echo("  ${Theme.iconSuccess} Preview sidecar ${Theme.muted("(port 8383)")}: ${Theme.success("running")} ${Theme.info("http://localhost:8383")}")
        } else {
            echo("  ${Theme.iconWarning} Preview sidecar ${Theme.muted("(port 8383)")}: ${Theme.warning("not reachable")}")
            echo("    ${Theme.iconInfo} ${Theme.muted("Tip: Start the preview sidecar service via Docker Compose.")}")
        }

        // 3. Summary
        echo("")
        echo(Theme.muted("──────────────────────────────────────────────────"))

        val isHealthy = report.allToolsInstalled && report.projectsWritable
        if (isHealthy) {
            val installedCount = report.toolStatuses.count { it.isInstalled }
            val totalCount = report.toolStatuses.size
            echo("${Theme.iconSuccess} ${Theme.success("Master Dev Workspace is healthy")} ${Theme.muted("($installedCount/$totalCount tools ready)")}")
        } else {
            val missingCount = report.missingTools.size
            if (missingCount > 0) {
                echo("${Theme.iconFailure} ${Theme.danger("Workspace has $missingCount missing tool(s):")} ${report.missingTools.joinToString(", ")}", err = true)
            }
            if (!report.projectsWritable) {
                echo("${Theme.iconFailure} ${Theme.danger("Projects volume is not writable.")}", err = true)
            }
            throw ProgramResult(1)
        }
    }
}
