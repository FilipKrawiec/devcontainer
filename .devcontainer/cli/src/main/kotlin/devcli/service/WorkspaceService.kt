package devcli.service

import devcli.model.WorkspaceRef
import java.io.File

data class FetchSummary(
    val relativePath: String,
    val success: Boolean,
    val errorMessage: String? = null
)

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

    fun findMatchingWorkspaces(target: String): List<String> {
        val cleanTarget = target.trim().removePrefix("/projects").trim('/')
        if (cleanTarget.isBlank()) return listWorkspaces()

        val allWorkspaces = listWorkspaces()
        return allWorkspaces.filter { workspace ->
            workspace == cleanTarget || workspace.endsWith("/$cleanTarget")
        }
    }

    fun fetchWorkspaces(
        targetRepoPath: String? = null,
        prune: Boolean = false,
        tags: Boolean = false
    ): Result<List<FetchSummary>> {
        if (!projectsRoot.exists()) {
            return Result.failure(IllegalStateException("Projects directory ${projectsRoot.path} does not exist"))
        }

        val workspacesToFetch: List<String> = if (!targetRepoPath.isNullOrBlank()) {
            val matched = findMatchingWorkspaces(targetRepoPath)
            if (matched.isEmpty()) {
                return Result.failure(IllegalArgumentException("Repository '$targetRepoPath' not found in ${projectsRoot.path}"))
            }
            matched
        } else {
            listWorkspaces()
        }

        if (workspacesToFetch.isEmpty()) {
            return Result.success(emptyList())
        }

        val total = workspacesToFetch.size
        println("FETCHING GIT REMOTES IN ${projectsRoot.path}:")
        println("--------------------------------------------------")

        val results = mutableListOf<FetchSummary>()
        workspacesToFetch.forEachIndexed { index, relPath ->
            val repoDir = File(projectsRoot, relPath)
            println("[${index + 1}/$total] Fetching remotes for $relPath...")

            val command = mutableListOf("git", "fetch", "--all")
            if (prune) command.add("--prune")
            if (tags) command.add("--tags")

            try {
                val process = ProcessBuilder(command)
                    .directory(repoDir)
                    .inheritIO()
                    .start()

                val exitCode = process.waitFor()
                if (exitCode == 0) {
                    println("Successfully fetched remotes for $relPath\n")
                    results.add(FetchSummary(relativePath = relPath, success = true))
                } else {
                    System.err.println("error: git fetch failed for $relPath with exit code $exitCode\n")
                    results.add(FetchSummary(relativePath = relPath, success = false, errorMessage = "git fetch failed with exit code $exitCode"))
                }
            } catch (e: Exception) {
                System.err.println("error: failed to execute git fetch for $relPath: ${e.message}\n")
                results.add(FetchSummary(relativePath = relPath, success = false, errorMessage = e.message))
            }
        }

        return Result.success(results)
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

