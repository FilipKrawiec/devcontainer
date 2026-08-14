package devcli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import devcli.service.RuntimeService

class DoctorCommand(
    private val runtimeService: RuntimeService = RuntimeService()
) : CliktCommand(
    name = "doctor",
    help = "Verify the default Master Dev Workspace runtime"
) {
    override fun run() {
        val report = runtimeService.inspect()

        if (report.missingTools.isEmpty()) {
            echo("Tools: OK")
        } else {
            echo("Tools: missing ${report.missingTools.joinToString(", ")}", err = true)
        }

        echo("Projects volume: ${if (report.projectsWritable) "writable" else "not writable"}")
        echo("Docker socket: ${if (report.dockerSocketAvailable) "available" else "not available"}")
        echo("Preview sidecar (port 8383): ${if (report.previewSidecarAvailable) "running (http://localhost:8383)" else "not running"}")

        if (report.missingTools.isNotEmpty() || !report.projectsWritable) {
            throw ProgramResult(1)
        }
    }
}
