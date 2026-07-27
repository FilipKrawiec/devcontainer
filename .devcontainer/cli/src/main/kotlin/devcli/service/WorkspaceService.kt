package devcli.service

import devcli.model.WorkspaceRef
import java.io.File

class WorkspaceService(
    private val projectsRoot: File = File("/projects")
) {
    private fun ensureProjectsRootWritable() {
        if (!projectsRoot.exists()) {
            projectsRoot.mkdirs()
        }
        if (projectsRoot.exists() && !projectsRoot.canWrite()) {
            try {
                ProcessBuilder("sudo", "chown", "-R", "vscode:vscode", projectsRoot.absolutePath)
                    .start()
                    .waitFor()
            } catch (_: Exception) {
                // Ignore if sudo is unavailable or fails
            }
        }
    }

    fun cloneWorkspace(repoUrl: String): Result<String> {
        return try {
            val ref = WorkspaceRef.fromRemote(repoUrl)
            val targetDir = File(ref.targetDirectoryPath)

            if (targetDir.exists() && File(targetDir, ".git").exists()) {
                return Result.failure(IllegalStateException("Repository already exists at ${ref.targetDirectoryPath}"))
            }

            ensureProjectsRootWritable()

            val parentDir = targetDir.parentFile
            if (parentDir != null && !parentDir.exists()) {
                val created = parentDir.mkdirs()
                if (!created && !parentDir.exists()) {
                    return Result.failure(IllegalStateException("Failed to create leading directory ${parentDir.path}. Please check permissions on /projects."))
                }
            }

            println("Cloning ${ref.remoteUrl} into ${ref.targetDirectoryPath}...")
            val process = ProcessBuilder("git", "clone", ref.remoteUrl, ref.targetDirectoryPath)
                .inheritIO()
                .start()

            val exitCode = process.waitFor()
            if (exitCode == 0) {
                Result.success(ref.targetDirectoryPath)
            } else {
                Result.failure(RuntimeException("git clone failed with exit code $exitCode"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun listWorkspaces(): List<String> {
        if (!projectsRoot.exists()) return emptyList()

        return projectsRoot.walkTopDown()
            .maxDepth(6)
            .filter { it.isDirectory && it.name == ".git" }
            .map { gitDir -> gitDir.parentFile.relativeTo(projectsRoot).path }
            .sorted()
            .toList()
    }

    fun removeWorkspace(relativeRepoPath: String): Result<String> {
        val targetDir = File(projectsRoot, relativeRepoPath.trim('/'))
        if (!targetDir.exists()) {
            return Result.failure(IllegalArgumentException("Repository not found at ${targetDir.path}"))
        }

        return try {
            targetDir.deleteRecursively()
            Result.success(targetDir.path)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
